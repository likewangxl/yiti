# KPI 计算改造（员工驱动 + 目标值驱动）设计与规则

> 交付：2026-06-17，commit `072de2e6`。本文按**当前生产代码** `KpiScoreCalcService` 整理，替代旧的「宽表对象驱动 + kpi_scheme_id 关联 + cycleKey 派生」模型。

## 1. 触发入口

- **事件驱动（主）**：指标计算完成发布 `MetricCalcCompletedEvent` → `KpiCascadeListener`（`@TransactionalEventListener` AFTER_COMMIT + `@Async` + Redis SETNX 30s 防重）触发重算。
- **手动 / 按方案**：`PerfCalcApi.triggerKpiCalc` / `KpiApi.triggerKpiCalc` → `KpiScoreCalcService.calculate(dataDate, schemeCode?, triggerType, triggerBy, runLogId?)`。

## 2. 总流程 `calculate(...)`

1. 建 `PERF_METRIC_CALC_TASK`，status=RUNNING（task.id 复用 `SYS_JOB_RUN_LOG.id`，无则 UUID）。
2. **前置依赖检查**：当日 **1/2/3 级指标都必须各有 SUCCESS 记录**，否则任务置 FAILED 并中止（KPI 依赖指标结果先算完）。
3. 取方案：指定 `schemeCode` → 单个；否则取**全部 status=ACTIVE** 的 KPI 方案。
4. **逐方案 `calcOneScheme`**（方案级隔离：单方案失败记 `PERF_KPI_CALC_LOG` 后继续下一个，不中止整任务）。
5. 汇总 success/fail/scored/skipped 写回任务终态。

## 3. 单方案 `calcOneScheme(scheme, dataDate)`

### 3.1 全量替换
落库前 `scoreMapper.deleteByDateAndScheme(dataDate, schemeCode)` 删除该数据日期+该方案的旧计分明细，避免脏数据。

### 3.2 确定员工范围（empUniverse）
**基础集（始终）= 目标值里出现过的员工**：`selectDistinctActiveEmpSubjects(metricCodes, dataDate)` —— 取「目标方案 ACTIVE + 目标值起止日期涵盖数据日期 + 指标∈本方案 KPI 指标」的去重 EMP 工号。

再按「员工角色范围」`emp_role_scope` 收窄：
| `emp_role_scope` | 员工全集 |
|---|---|
| **空** | = 基础集（不过滤）|
| **非空** | = **基础集 ∩ 所选角色成员**（`UserApi.getEmpIdsByRoleCode`(USER_ID) → 工号，与基础集取交集）|

- 员工全集为空（基础集 ∩ 角色范围 无匹配）→ 本方案不计分（返回 0/0）。
- 批量解析 **工号 → 所属机构 `mainOrgCode`**（`resolveEmpOrgMap`，经 `UserApi.getUsersByUsernames`），供 ORG 维度指标取机构数。

### 3.3 逐「KPI 指标项 × 员工」计算
对方案每个 `perf_kpi_item`：
- 指标定义不存在 / 状态非已发布(ACTIVE) → **跳过**该项（skipped++）。
- `val_slot` 非法（null 或 不在 1..400）→ 抛 `METRIC_CALC_LOGIC_INVALID`。
- **仅支持 EMP / ORG 维度**；CUST / 维度无关 → 跳过。
- 既无计算表达式也无 SQL 表达式 → 跳过。
- 一次性载入该 `slot+维度` 的实际值映射 `actualMap`（`loadSubjectValues`）：EMP→`EMP_INDEX_RESULT[工号]`，ORG→`ORG_INDEX_RESULT[机构]`。

对 `empUniverse` 中每个员工：

| 指标维度 | 目标值对象 (subject) | 实际值键 (actualKey) |
|---|---|---|
| EMP | (EMP, 工号) | 工号 → EMP宽表 |
| ORG | (ORG, **员工所属机构** mainOrgCode) | 机构 → ORG宽表；**员工无机构 → 跳过** |

- 取目标值：`selectActiveCoveringByDimSubjectMetric(subjectType, subjectId, metricCode, dataDate)` —— **目标方案 status=ACTIVE 且 目标值 `start_date ≤ dataDate ≤ end_date`**。
- **该「员工/机构 × 指标」在目标值中未定义（tv=null）→ 不计算，跳过**。
- `target` / `base` 取自目标值（null→0）；`actual` 取自 `actualMap`（缺→0）。
- **算分**：有计算表达式 → `KpiScoreFormulaService.evalScore`（Groovy）；否则 → `evalScoreBySql`（SQL）。每指标只用一个表达式。
- **落库** `upsertScore`：**subject_type=EMP、subject_id=工号、metric_code=该指标**（机构维度指标也按工号落 `PERF_KPI_SCORE`），scored++。

## 4. 计分规则

- **表达式优先级**：计算表达式 `formula`（Groovy，裸变量名，如 `actual / target * weight`）**优先**；未配置时回退 SQL 表达式 `sqlExpr`（NamedParameter JDBC，`:占位符`）。**每个指标只用一个表达式**。
- **可用变量**：`actual`（实际值）/ `target`（目标值）/ `base`（基础值）/ `weight`（权重）/ `maxScore`（计分上限）/ `minScore`（计分下限）。得分公式由指标项自行配置，引擎不写死（默认示例 `min(max(actual/target*weight, minScore), maxScore)`）。
- **目标值不重叠**：同方案「同对象+同指标」的阶段 `[start_date, end_date]` 闭区间**不可重叠**（`PERF-40024 TARGET_VALUE_DATE_OVERLAP` 校验保证某数据日期至多命中一条）。
- **跳过（skipped）汇总条件**：指标不存在/非已发布、非 EMP/ORG 维度、无表达式、ORG 员工无所属机构、无目标值。
- **结果唯一键**：`dataDate + schemeCode + metricCode + subject_type(EMP) + subject_id(工号)`。

## 5. 关键 Mapper / 依赖

| 用途 | 方法 |
|---|---|
| 角色范围为空时的员工全集 | `PerfTargetValueMapper.selectDistinctActiveEmpSubjects(metricCodes, dataDate)` |
| 按维度对象+指标取覆盖数据日期的目标值 | `PerfTargetValueMapper.selectActiveCoveringByDimSubjectMetric(subjectType, subjectId, metricCode, dataDate)` |
| 工号→所属机构 | `UserApi.getUsersByUsernames` → `UserDTO.mainOrgCode` |
| 实际值宽表 | `EmpIndexResultMapper` / `OrgIndexResultMapper`.`selectLatestSlotValuesByDate(dataDate, slot)` |

## 6. 与旧模型的关键差异

| 维度 | 旧 | 新 |
|---|---|---|
| 计算驱动 | 宽表对象集驱动 | **员工集合 × 指标**（员工驱动）|
| 目标方案关联 | `kpi_scheme_id` 直连 + 由 `targetCycle` 派生 cycleKey 查目标值 | **不再用 kpi_scheme_id**；按 (维度对象, 指标) + 目标值起止日期涵盖数据日期 查 |
| 员工范围 | 全宽表对象（角色范围仅 EMP 过滤） | **基础集=目标值里出现过的员工**；角色范围非空时在基础集上取交集，为空时即基础集 |
| ORG 指标 | 按机构对象计分 | 按**员工所属机构**取机构实际值/目标值，得分仍落**员工工号** |
| 无目标值 | 仍计分（target=0） | **跳过不计算** |

## 7. 测试

- `KpiScoreCalcServiceTest` 23 case 全绿（含 角色范围空/非空、EMP/ORG 维度取数、无目标值跳过、表达式优先级）。
- 关联：`TargetValueServiceTest`（目标值阶段日期不重叠 27）、`TargetPlanServiceTest`/`ScopeTest`（方案删除 23+5）。
