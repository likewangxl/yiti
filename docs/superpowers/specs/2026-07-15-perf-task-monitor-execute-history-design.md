# 指标重算任务监控页 — 新增/执行/批量执行/历史 设计方案

- 日期：2026-07-15
- 模块：performance-engine-center（前端 `xanzc_frontend`）
- 页面：`xanzc_frontend/src/views/perf/TaskMonitor.vue`
- 相关表：`PERF_RUN_TASK`（复用，**零 schema 变更**）
- 状态：设计已确认（S2 + 批量执行 B2），待评审后转实施计划

---

## 1. 背景与目标

现有"任务监控"页（`TaskMonitor.vue`）是**只读**的：固定过滤 `taskType=METRIC_RUN` 的执行日志，只能查看与看详情。

本次要把它升级为可操作的"指标重算任务"管理页，交付 4 个能力：

1. **新增**：从监控页选择一个指标发起一次回算。
2. **执行**：对列表中已有指标再次发起回算。
3. **批量执行**：多选若干指标，选一个数据日期，一次性对所选指标发起回算。
4. **历史**：查看某指标的历次回算执行过程（右侧抽屉）。

并且要求：**指标库页面点击"立即执行"发起的回算，也要自动出现在本监控列表里**。

> 演进说明：需求初始表述为"新增/编辑/删除"。经设计讨论收敛为 **新增 · 执行 · 批量执行 · 历史**——
> "编辑"因为唯一可编辑的参数（数据日期）已挪到执行时选择而失去意义，故去掉；
> "删除"本期不做（`先不做删除按钮`）。决策过程见 §8。

---

## 2. 现状核实（关键事实）

在设计前核实了代码与线上库，以下事实决定了方案骨架：

1. **`PERF_RUN_TASK` 是执行日志表**，由计算引擎写入。`MetricCalcService.calcMetric(...)`
   每次执行写一行：`taskType=METRIC_RUN`、`task_key=指标码`、`data_date`、`status`（PENDING→终态）、
   `started_by`、`start_time/end_time`、`error_msg`、`result_preview_json`。

2. **指标库"立即执行"早已写这张表**：`Metrics.vue` 的 ⚡立即执行 → `POST /api/perf/metrics/{code}/execute`
   → `calcMetric`。其对话框**已经在收集"数据日期 + 原因"**。因此监控页与指标库立即执行**本就共用
   `PERF_RUN_TASK`**——"合并"在数据层已经成立。

3. **`task_key` 不唯一**。真实 DDL（`docs/schema/ddl-performance.sql`）中 `PERF_RUN_TASK` 只有
   `PRIMARY KEY(id)` + 普通索引（`idx_task_type/idx_status/idx_started_by/idx_created_time/idx_type_trigger`），
   **没有 `uk_task_key`**。线上 `yiti` 库该表约 6601 行，单个指标已有数十行（如 `MC_003` 54 行、`M_0340` 54 行）。
   → 「计算次数 = 按指标 COUNT」天然可行。
   > 文档勘误：`performance-engine-center/CLAUDE.md` 与根 `CLAUDE.md` Runbook 里"perf_run_task uk_task_key（已应用）"
   > 的记载与实际不符——该唯一键**并未存在**于 `yiti` 库。

4. **历史查询无需新端点**：`RunTaskQuery` 已含 `taskKey` 字段，现有
   `GET /api/perf/run-tasks?taskKey=X` 即可拉某指标的全部执行行（`P_PERF_RT_LIST` 资源）。

5. **单指标执行无需新端点**：`POST /api/perf/metrics/{code}/execute` 已存在（高危 + `@AuditLog(reasonRequired=true)`），
   前端 `executeMetric(code, {dataDate, reason})` 也已就绪，`version` 由该链路服务端自动解析当前生效版本。

6. **`/api/perf/recalc` 不适合作批量执行底座**：`RecalcReqDTO.cycleType` 为 `@NotBlank` 且仅
   `MONTHLY/QUARTERLY/YEARLY`（无 DAILY），对"任意指标按某数据日期执行"语义不匹配，且会额外产生父级 `RECALC` 行。
   → 批量执行改为新增专用端点（B2，见 §6.2）。

结论：**复用 `PERF_RUN_TASK`，不建新表、不改 schema**；后端新增两个端点——"按指标分组汇总"只读端点 + "批量执行"写端点。

---

## 3. 范围

### 做（In scope）

- 列表页改为**按指标分组**展示：（多选框）/ 指标 / 创建时间 / 计算次数 / 操作。
- **新增**（S2：新增即执行）：`任务类型`(字典) + `指标`(联动下拉+模糊) + `原因` + `执行时间` → 立即同步执行。
- **执行**（列表每行）：`执行时间` + `原因` → 对该行指标立即同步执行。
- **批量执行**（多选）：每行多选框 + 工具栏"批量执行"按钮 → 选 `数据日期` + `原因` → 对所选指标逐个同步执行，服务端 best-effort 聚合。
- **历史**（列表每行）：右侧抽屉，展示该指标历次执行（执行时间/开始/结束/计算结果/发起人）。
- 后端新增：①"按指标分组汇总"只读端点；②"批量执行"写端点。均含对应 `PT_RESOURCE` 资源。
- `PERF_TASK_TYPE` 字典种子（目前仅一项"指标重算"）。

### 不做（Out of scope）

- ❌ 编辑、删除。
- ❌ 定时/后台调度自动执行（执行为手动、同步）。
- ❌ 单次执行支持多数据日期区间（批量执行只针对多指标 + 单数据日期；日期区间回算仍走既有 `/recalc`）。
- ❌ 新增独立任务登记表 / `PERF_RUN_TASK` 加列（零 schema 变更）。
- ❌ "未进行"占位行（S2 已去除该状态）。

---

## 4. 交互设计

### 4.1 列表页（按指标分组）

| 列 | 含义 | 数据来源 |
|---|---|---|
| ☐ 多选 | 批量执行选择 | `el-table type="selection"`（带表头全选）|
| 指标 | 指标编码 + 中文名 | `task_key` + 指标名（复用 `enrich` 走 `PerfMetricDefMapper`）|
| 创建时间 | 该指标首次进入回算的时间 | `MIN(created_time)`（语义=首次执行时间，沿用需求方"创建时间"叫法）|
| 计算次数 | 该指标累计执行行数 | `COUNT(*)`（`taskType=METRIC_RUN`）|
| 操作 | 执行 · 历史 | — |

- 顶部保留轻量过滤：`任务类型`(字典下拉) + `指标关键字`（编码/名称模糊）。原只读页的 状态/数据日期/发起人 过滤在分组视图下意义不大，移除。
- 顶部操作区：**刷新** + **新增** + **批量执行**（勾选 ≥1 行才可用）。
- 分组口径默认纳入该指标**全部** `METRIC_RUN` 行（含指标库 MANUAL、回算 RECALC、调度 SCHEDULED），使"计算次数"= 该指标被计算的总次数。

### 4.2 新增（弹窗，S2 新增即执行）

字段顺序：

1. `任务类型`：字典下拉（`PERF_TASK_TYPE`），目前仅"指标重算"，默认选中。
2. `指标`：联动下拉——任务类型=指标重算时列出**全部指标**（`GET /api/perf/metrics`），支持**模糊搜索**（`el-select filterable`，客户端过滤编码/名称）。必填。
3. `原因`：必填（执行端点 `reasonRequired=true`）。
4. `执行时间`：`el-date-picker`（`value-format=YYYY-MM-DD`），= 数据日期，**默认空、必填**。

确定 → `executeMetric(指标码, { dataDate: 执行时间, reason: 原因 })` → 同步执行 → 刷新列表。

### 4.3 执行（列表每行，弹窗）

- 与"新增"共用同一执行对话框组件，区别：**指标已锁定为该行指标**（不可改），只填 `原因` + `执行时间`。
- 确定 → `executeMetric(row.metricCode, { dataDate, reason })` → 刷新。

### 4.4 批量执行（多选，弹窗）

- 前置：勾选 ≥1 行；工具栏"批量执行"可点。
- 弹窗字段：`数据日期`（必填、默认空）+ `原因`（必填，一条原因套用整批）。
- 确定 → `batchExecuteMetrics({ metricCodes: 所选指标码[], dataDate, reason })`（§6.2 新端点）→
  服务端逐个 `calcMetric` best-effort 执行 → 返回聚合结果 → 前端提示"成功 X / 失败 Y（失败明细）" → 清空勾选 + 刷新。
- 每个指标各写一行 `METRIC_RUN`，因此各自"计算次数"+1、历史各自留痕。

### 4.5 历史（列表每行，右侧抽屉）

- 点击"历史"打开 `el-drawer`（`size≈52%`，右侧，沿用现有 detail 抽屉样式）。
- 抽屉内为该指标历次执行的**表格**（`listRunTasks({ taskKey: row.metricCode, pageNo, pageSize })`，按 `created_time` 倒序）：

  | 列 | 来源 |
  |---|---|
  | 执行时间 | `created_time`（触发时刻）|
  | 开始时间 | `start_time` |
  | 结束时间 | `end_time` |
  | 计算结果 | `status`（状态标签）+ 失败时 `error_msg` / 成功时 `result_preview_json`（行内展开或 tooltip）|
  | 发起人 | `started_by`（+ 姓名，复用 enrich）|

- 抽屉内分页，复用现有分页组件。

---

## 5. 数据模型

- **复用 `PERF_RUN_TASK`，零 schema 变更**（不建新表、不加列）。
- 每次执行（含新增/执行/批量执行/指标库立即执行）= 一行 `METRIC_RUN`，由现有 `calcMetric` 写入，字段沿用现状。
- 分组汇总为纯查询，不落任何新状态。
- **字典种子**（DML，非 DDL）：`PERF_TASK_TYPE` 字典 + 一项 `指标重算`。走 governance `DictApi`（`DictFacade.toItemDTO(SysDict)` 从 **`SYS_DICT`** 表读，前端 `/sys/dicts/PERF_TASK_TYPE/items`），
  幂等插入脚本置于 `docs/superpowers/sql/2026-07-15-perf-task-monitor-dict-and-resources.sql`。

---

## 6. 后端设计

### 6.1 新增端点：按指标分组汇总

- 路径：`GET /api/perf/run-tasks/metric-summary`
- 鉴权：`@BizAuth(bizType = PERF_CONFIG, action = LIST)`；新增资源 `P_PERF_RT_SUM`（对齐既有 `P_PERF_RT_*` 命名）。
- 入参：`taskType`(默认 METRIC_RUN) / `metricKeyword`(编码或名称模糊) / `pageNo` / `pageSize`。
- 出参：分页 `List<MetricSummaryDTO>`：`{ metricCode, metricName, firstCreatedTime, runCount, lastStatus? }`。
- 实现：`PerfRunTaskMapper` 新增 `selectMetricSummary` / `countMetricSummary`（`GROUP BY task_key`，
  `COUNT(*)`、`MIN(created_time)`）；`PerfRunTaskService` 装配指标名（复用 `enrich` 思路，零 N+1）。
- **数据范围**：复用现有 `resolveScopeFilter()`（非管理员追加 `AND started_by = '<empId>'`），
  分组前先按数据范围收敛。SQL 片段注入沿用现有 `${dataScopeFilter}` 合法例外约束（仅可信来源）。

### 6.2 新增端点：批量执行（B2）

- 路径：`POST /api/perf/metrics/batch-execute`（归属 `MetricDefController`，与单执行 `.../{code}/execute` 同域）。
- 鉴权：`@BizAuth(bizType = PERF_CONFIG, action = EXECUTE)`；新增资源 `P_PERF_MTR_BEXEC`（≤20 字符，高危，绑定与单执行资源 `P_PERF_MTR_EXEC` 相同的角色集）。
- 审计：`@AuditLog(action = "PERF_METRIC_BATCH_EXECUTE", resourceType = "PERF_RUN_TASK", reasonRequired = true)` —— **整批一条审计**。
- 入参 DTO `BatchExecuteReqDTO`：
  - `metricCodes: List<String>` —— `@NotEmpty`，且 `@Size(max = 50)`（同步执行防超时的软上限，可配置）。
  - `dataDate: LocalDate` —— `@NotNull`（`yyyy-MM-dd`）。
  - `reason: String` —— `@NotBlank`，`@Size(max = 500)`（与单执行 `MetricExecuteReqDTO` 一致）。
- 出参 DTO `BatchExecuteRespDTO`：`{ total, success, failed, results: [{ metricCode, status, runTaskId?, errorMsg? }] }`。
- 实现（新增/扩展 service，如 `MetricBatchExecuteService` 或复用既有 `MetricBatchCalcService`）：
  - `version` 服务端解析当前生效版本（与单执行同一逻辑，前端不传）。
  - **best-effort 聚合**：逐指标 `calcMetric(metricCode, dataDate, version, "MANUAL")`，仿 `HistoryRecalcService`
    对单指标失败 `try/catch` 累计而不中断整批；每指标独立写自己的 `PERF_RUN_TASK` 行与状态。
  - **不开最外层 `@Transactional`**：各指标 run_task 独立提交，失败路径仍可追溯（与 `MetricCalcService`/`HistoryRecalcService` 一致）。
  - `triggerType` 沿用 `MANUAL`（与单执行统一；如需区分批量来源可后续加 `BATCH` 值，本期不做）。

### 6.3 复用端点（不改）

- **历史**：`GET /api/perf/run-tasks?taskKey={metricCode}`（`P_PERF_RT_LIST`）。
- **新增/单执行**：`POST /api/perf/metrics/{code}/execute`（已有，高危 + `@AuditLog(reasonRequired=true)`，版本自动取当前生效版本）。
- **字典读取**：现有 governance 字典查询端点（前端读 `PERF_TASK_TYPE`）。
- **指标下拉**：`GET /api/perf/metrics`（V1.10 起返回全量 `List`）。

### 6.4 权限与审计

- 监控页"执行/新增/批量执行"实为发起指标计算 → 使用者须具备**指标执行权限**（高危、写审计）。本页不弱化该约束。
- 分组汇总端点为只读 `LIST`，不写审计。

---

## 7. 前端设计（`TaskMonitor.vue` 重写）

- 列表数据源由 `listRunTasks` 改为新的 `listMetricSummary`（`api/perf.js` 新增）。
- 过滤区：`任务类型`(字典下拉) + `指标关键字`。
- 顶部操作区：`刷新` + `新增` + `批量执行`（`:disabled="selected.length===0"`）。
- 表格列：多选(`type="selection"`) / 指标 / 创建时间 / 计算次数 / 操作(执行·历史)；`@selection-change` 维护 `selected`。
- 组件：
  - **执行对话框**（新增与执行共用）：字段随"是否锁定指标"切换；调用 `executeMetric`。
  - **批量执行对话框**：`数据日期` + `原因`；调用 `batchExecuteMetrics`；结果按 `success/failed` 汇总提示。
  - **历史抽屉**：右侧 `el-drawer` + 执行记录表格 + 分页，数据走 `listRunTasks({ taskKey })`。
- 复用现状：状态字典/配色（`STATUS_MAP`、`tag-*`）、时间格式化 `fmtTime`、JSON 美化 `fmtJson`。
- `api/perf.js` 新增：`listMetricSummary(params)`、`batchExecuteMetrics(payload)`；`executeMetric` 已存在直接复用。

---

## 8. 边界与决策记录

| 决策 | 结论 | 理由 |
|---|---|---|
| 任务对象语义 | 回算任务计划（非日志行 CRUD、非 Quartz 调度）| 用户选定 |
| 执行方式 | 手动"执行"按钮 + 同步复用现有引擎 | 改动最小、风险最低 |
| 任务粒度 | 单指标单数据日期 | 直接复用 `calcMetric` / execute 端点 |
| "执行时间"语义 | = 要计算的数据日期（C）| 故创建时不再单独填数据日期 |
| 编辑 | 去掉 | 数据日期挪到执行时选择后，任务无可编辑参数 |
| 删除 | 本期不做 | 用户明确"先不做删除按钮"；历史执行记录另有 90 天清理任务自然过期 |
| 新增 vs 执行 | S2：新增即执行，无"未进行"占位行 | 用户选 S2，减少操作流程 |
| 批量执行实现 | B2：新增专用后端端点 `POST /metrics/batch-execute` | 用户选 B2，更稳定：一次请求/一条审计/服务端聚合 |
| 批量不走 `/recalc` | 是 | `cycleType` 强约束 MONTHLY/QUARTERLY/YEARLY，语义不匹配单日期任意指标 |
| 存储 | 复用 `PERF_RUN_TASK`，零 schema 变更 | `task_key` 非唯一、执行数据已在此表；避免第二张表 |
| 计算次数纳入范围 | 指标全部 `METRIC_RUN` 行 | 反映"该指标被计算总次数"，含指标库立即执行 |

---

## 9. 测试策略

遵循模块 TDD 红线（先红后绿、独立提交）：

- **Mapper（failsafe IT）**：`selectMetricSummary`/`countMetricSummary` 的 `GROUP BY`/`COUNT`/`MIN`
  正确性；数据范围片段生效（管理员全见 vs 仅见自己 `started_by`）。
- **Service（surefire UT）**：
  - 分组装配指标名零 N+1；空结果分页；关键字过滤。
  - **批量执行**：全成功 / 部分失败（某指标抛异常不中断整批）/ 全失败 的聚合计数；`metricCodes` 超上限拒绝（VALIDATION_FAILED）；version 自动解析。
- **Controller（IT）**：
  - `GET /run-tasks/metric-summary` 鉴权（`P_PERF_RT_SUM` / 401 未登录）、分页契约。
  - `POST /metrics/batch-execute` 鉴权（`P_PERF_METRIC_BEXEC`）、`@AuditLog(reasonRequired)` 反射校验、入参校验（空列表/无日期/无原因/超上限）。
- **前端**：新增/执行对话框校验（指标必填、原因必填、执行时间必填）；批量执行多选联动 + 结果汇总；历史抽屉按 `taskKey` 拉取与分页；指标下拉模糊搜索。
- **回归**：确认指标库"立即执行"产生的行出现在监控汇总中（同 `task_key` 计数 +1）。

---

## 10. 未做 / 后续

- 删除能力（需独立高危 URL + 审计 + 历史保留策略）留待后续需求。
- 批量执行的 `triggerType=BATCH` 独立取值（历史里区分"批量/单次"来源）为可选增强，本期沿用 `MANUAL`。
- 批量执行为同步，`metricCodes` 有软上限（50）防超时；若需大批量，后续可评估异步任务化。
- 若未来"任务类型"扩展（如 KPI 重算），联动下拉的"可选项来源"按类型分支扩展即可，端点契约不变。
- 分组汇总当前对单指标全量行 `COUNT`；若数据量增长需评估 `task_key` 上加索引或物化计数。
