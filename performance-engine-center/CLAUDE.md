# performance-engine-center/ CLAUDE.md

本文件为 `performance-engine-center` 模块提供上下文说明。

## 模块概述

**performance-engine-center** 是绩效计算中心（核心域），为整个平台提供指标库管理、KPI 方案设计、目标管理、客户分配关系查询、数据版本控制、调整审批流程、异步导出、数据范围注入等能力。

**当前版本**: V1.3（技术债清偿）—— 在 V1.2 流程/事件/导出/数据范围之上清偿 V1.1/V1.2 累积技术债：
- R0 V1_2_5 NULL deleted 清理 + V1_3_0 uk_task_key 补齐 + failsafe 分层 + CLAUDE.md 勘误
- R1 Target 数据范围注入（TargetValue + TargetPlan + Controller 三级，复用 PerfScopeHelper）
- R2 5 处 V1.2 UOE 全部实际实现（PerfCalcApi.triggerKpiCalc / MetricQueryApi.3 snapshot / MetricApi.getUserMetricCards），Facade UOE 清零
- R3 PERF-50003 IDEMPOTENCY_WAIT_TIMEOUT 新增 + MetricTrialRespDTO 字段对齐 03 §A.5 + MetricDefController.execute 返回 RunTaskInfoDTO
- R4 11 Controller 局部 entity 清零（NoEntityInControllerLocalsArchTest 守护）+ P7 recalc 真实 status + cycleType 写 params_json
- R5 Testcontainers-redis 接入（2 个 Redis IT 激活）+ UndoScriptSmokeIT 重写支持 V1_3_0 基线 + NoUoeInFacadeTestsArchTest 守护（facade 测试层 UOE 清零）

（V1.2 历史：Q1 版本回滚 / Q2-Q3 审批 BPMN / Q4 4 类领域事件 / Q5 ShedLock / Q6 4 导出策略 / Q7 7 种 DataScopeType / Q8 surefire 假绿修复 + 45 资源激活）

**基础包名**: `com.bank.branch.platform.performance`
**Maven 坐标**: `com.bank.branch.platform:performance-engine-center`

**Spec**: `docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md`（v1.2）
**Plan**: `docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md`

## 分期策略

| 版本 | 范围 | 状态 |
|---|---|---|
| V1.0 | 配置态 CRUD + 版本管理骨架 + 只读查询 + 7 个 Api（契约定型）| 已交付 |
| V1.1 | 指标执行（SQL+Groovy+级联）、KPI 计算（定时任务+手动触发）、数据导入（Excel/SQL/外部上报 3 策略）、历史回算（父子 run_task） | 已交付 |
| V1.2 | 分配/目标调整审批（BPMN + Flowable）、4 类领域事件发布、4 导出策略（异步任务 + MinIO）、ShedLock 分布式锁、PerfScopeHelper 数据范围注入、PT_RESOURCE 资源全量激活（45 条） | 已交付 |
| **V1.3** | 技术债清偿：V1_2_5/V1_3_0 DDL 兜底、Target 数据范围注入、5 处 V1.2 UOE 实际实现、PERF-50003 新增、MetricTrialRespDTO 对齐 03 §A.5、execute 返回 RunTaskInfoDTO、11 Controller 局部 entity 清零、P7 recalc 真实 status、cycleType 写 params_json、Testcontainers-redis 接入、UndoScriptSmokeIT 重写、2 个新架构守护（NoEntityInControllerLocalsArchTest + NoUoeInFacadeTestsArchTest） | **本期交付（2026-04-24）** |

### V1.3 UOE 清单（Facade UOE 已清零）

V1.2 遗留的 5 处 Facade UOE 在 V1.3 R2 全部转为真实实现：

| Api.方法 | V1.3 交付任务 | 实现入口 |
|---|---|---|
| `PerfCalcApi.triggerKpiCalc` | R2.1 | `KpiCalcService.calcScheme(schemeCode, cycleType, cycleDate, asOfDate, version)` 委托入口，返回成功员工数 |
| `MetricQueryApi.batchQueryEmpSnapshots` | R2.2 | EmpIndexResultMapper.selectSlotValuesByEmps，500 条 subject + 50 条 metricCode 上限 |
| `MetricQueryApi.batchQueryOrgSnapshots` | R2.3 | OrgIndexResultMapper.selectSlotValuesByOrgs 同上 |
| `MetricQueryApi.batchQueryCustSnapshots` | R2.4 | CustIndexResultMapper.selectSlotValuesByCusts 同上 |
| `MetricApi.getUserMetricCards` | R2.5 | ACTIVE KPI 方案并集 + EMP 宽表 slot 读取 + TargetValue 按 planId=null 近似，mom/yoy 留 V1.4 |

Facade 层 UOE 清零，**新增**架构测试 `NoUoeInFacadeTestsArchTest` 守护 facade 测试层不得再写 `assertThrows(UnsupportedOperationException.class, ...)`（Task R5.3）。
旧守护 `NoV11UOEArchTest` 继续守护 facade/*.java 不出现 `"V1.1 delivered"` 字面量。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`, `system-governance-center`
- **V1.1 仍不依赖**: `workflow-center`（V1.2 才依赖，分配/目标调整审批时）、`customer-marketing-center`
- **被依赖（未来）**: `portal-content-center`、`report-analytics-center`、`customer-marketing-center`、`business-application-center`

## 包结构

```
src/main/java/com/bank/branch/platform/performance/
├── api/                    # 7 个对外 Api 接口 + 14 DTO + 1 Cmd
│   ├── MetricApi.java          (7 方法, V1.1 交付 6 实现 + 1 V1.2 UOE: getUserMetricCards)
│   ├── MetricQueryApi.java     (3 方法, 全部 V1.2 UOE: batchQuery*Snapshots 报表专用快照)
│   ├── KpiApi.java             (5 方法, V1.1 交付 4 实现 + 1 V1.2 UOE: triggerKpiCalc 统一入口由 KpiApi 承接)
│   ├── TargetApi.java          (4 方法, 全部 V1.0 实现)
│   ├── PerfCalcApi.java        (3 方法, V1.1 交付 2 实现 + 1 V1.2 UOE: triggerKpiCalc 契约冗余占位)
│   ├── DataTaskApi.java        (1 方法, V1.1 交付)
│   ├── AllocApi.java           (10 方法, 全部 V1.0 实现)
│   └── dto/ (14 DTO + cmd/1 Cmd)
├── config/                 # Spring 配置 (AutoConfig / MyBatis / Redis)
├── controller/             # REST 控制器 (V1.0/V1.1: 8 个 Controller, 35 端点)
├── facade/                 # 对外 Api 实现 + 分布式锁 (Facade 申请/释放)
├── service/                # 业务逻辑 (V1.1: 含 MetricCalcService/KpiCalcService/PerfImportService/HistoryRecalcService)
├── mapper/                 # MyBatis Mapper 接口 (V1.1: 含 *IndexResult/KpiResult Mapper, 模块私有)
├── entity/                 # 贫血模型 (V1.1: 含 *IndexResult/KpiResult 宽表 Entity)
├── enums/                  # 枚举 + 错误码
│   ├── PerfErrorCode.java      (30 个 PERF-* 错误码：V1.0 25 个 + V1.1 新增 4 个：42206 批量上限 / 40005 KPI 方案重复 / 40006 目标方案重复 / 40007 RunTask 不存在 + V1.3 R3.1 新增 1 个：50003 IDEMPOTENCY_WAIT_TIMEOUT)
│   ├── BaseDimEnum.java
│   ├── MetricLevelEnum.java
│   ├── CalcLogicTypeEnum.java
│   ├── CycleTypeEnum.java
│   ├── MetricStatusEnum.java
│   └── RunTaskStatusEnum.java
├── exception/
│   └── PerfException.java      (extends common-web BizException)
└── listener/               # V1.2 事件监听器, 本期空包

src/main/resources/
├── mapper/                 # MyBatis XML
└── sql/
    ├── V1_0_0__performance_ddl.sql        # 基线 DDL 副本 (v1.2 no-op)
    ├── V1_0_1__performance_resources.sql  # PT_RESOURCE 35 + pt_role_biz_scope 2
    └── V1_0_2__performance_dicts.sql      # sys_dict 10 + sys_dict_item 39
```

## V1.0 数据表 (13 张)

**配置表** (6 张)：`sys_control` / `perf_metric_def` / `perf_metric_ref` / `perf_kpi_scheme` / `perf_kpi_item` / `perf_target_plan`

**业务数据表** (2 张)：`perf_target_value` / `cust_alloc_relation`

**日志表** (1 张)：`perf_run_task`

**宽表仅建表** (4 张, V1.1 使用)：`emp_index_result` / `org_index_result` / `cust_index_result` / `kpi_result`

**V1.0 整改决策（2026-04-22）**：已通过 V1_0_3（DDL 字段与唯一键补齐）与 V1_0_4（PT_RESOURCE 规划资源注册+字典项同步）两批 Flyway 脚本补齐 DDL 偏离，详细过程见 `docs/superpowers/plans/2026-04-22-performance-v1.0-rectification-plan.md`。

## 关键设计原则

### 1. 严格 TDD 红线 (CLAUDE.md 根项目规则)

- 先写测试 → 运行失败 (红) → 写最简实现 → 运行通过 (绿) → 重构
- 每步独立 commit, 禁止批量提交
- code-reviewer 审查 git 历史, 不符合 TDD 节奏视为 Must Fix

### 2. 7 个对外 Api 契约 V1.0 定型, V1.1-V1.3 全部替换为实现（Facade UOE 清零）

V1.0 的 7 个对外 Api 签名全部按 `docs/modules/performance-engine-center/04-对外API契约.md` 定型。
V1.0 无法实现的 13 个方法原抛 `UnsupportedOperationException` 占位：
- V1.1 P 阶段交付 9 个方法（MetricApi 3 + KpiApi 2 + PerfCalcApi 2 + DataTaskApi 1 + 内部补充 1）
- V1.3 R2 阶段交付剩余 5 个方法（Facade UOE 清零）：
  - `MetricApi.getUserMetricCards`（R2.5：ACTIVE KPI 方案并集 + EMP 宽表 slot 读取 + TargetValue 按 planId=null 近似；mom/yoy/previousValue 字段留 V1.4）
  - `MetricQueryApi.batchQueryEmpSnapshots / batchQueryOrgSnapshots / batchQueryCustSnapshots`（R2.2/R2.3/R2.4：宽表 selectSlotValuesByEmps/Orgs/Custs，500 subject + 50 metricCode 上限）
  - `PerfCalcApi.triggerKpiCalc`（R2.1：委托 `KpiCalcService.calcScheme`，签名从 V1.0 占位的 `(LocalDate) → String` 改为 V1.3 的 `(schemeCode, cycleType, cycleDate, asOfDate, version) → int`；与 `KpiApi.triggerKpiCalc` 双入口共存）

V1.3 相关架构守护：
- `NoV11UOEArchTest`：facade/*.java 不得再出现 `"V1.1 delivered"` 字面量（V1.1 引入）
- `NoUoeInFacadeTestsArchTest`：facade 测试层不得再写 `assertThrows(UnsupportedOperationException.class, ...)`（V1.3 R5.3 新增）

### 3. planId 类型 String（v1.2 修订）

**技术债声明**：04 契约文档原用 `Long planId`, 与生产 DDL `varchar(32)` 冲突。V1.0 对齐生产 DDL, 全局使用 `String planId`, 04 契约的修正由架构师后续统一处理。

**Task A2 已结清（2026-04-22）**：03/04/05 三份文档的主键类型已统一修订为 `String (varchar(32))`，技术债已结清。

### 4. 配置表缓存策略

Redis 缓存 `perf:metric_def:{code}`, `perf:kpi_scheme:{id}`, `perf:target_plan:{id}`, `perf:sys_control:{scopeDim}` 等。所有 evict 通过 `TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit` 回调触发，避免事务前脏数据污染缓存。

### 5. Redis 锁在 Facade 层申请 (v1.2)

Spring `@Transactional` 方法内无法在 "事务外" 申请锁。正确分层：
- `SysControlFacade.switchVersion` → 申请 Redis 锁 → 调 Service `@Transactional` 方法 → finally 释放锁
- `MetricApiImpl.allocSlot`（槽位分配）同理

### 6. 并发测试例外策略

`@Transactional + @Rollback` 与多线程不兼容（线程本地事务绑定）。
- 单线程 Mapper IT：继承 `PerformanceMapperTestBase`（含 @Transactional）
- 并发 Mapper IT：继承 `PerformanceConcurrentTestBase`（**不含** @Transactional），用 `TestDbCleaner` + 前缀隔离

### 7. BizType 使用 common-security 现有枚举

V1.0 使用 `BizType.PERF_CONFIG`（粗粒度）+ PT_RESOURCE ID `P_PERF_*`（细粒度）的组合模型，不扩展 common-security 的 BizType 枚举。

### 7.1 BizType 单档决策（2026-04-22）

经架构评审，performance-engine-center **不扩展 common-security 的 BizType 枚举**。所有 Controller 端点统一使用 `@BizAuth(bizType = BizType.PERF_CONFIG, action = <具体动作>)`，细粒度授权通过 `@BizAuth.action` 字段 + PT_RESOURCE 资源 ID (`P_PERF_*`) + 角色-资源绑定矩阵实现：

- 查询类端点：resourceId 形如 `P_PERF_METRIC_QUERY`、`P_PERF_KPI_QUERY`（角色绑定"绩效查询员"）
- 配置类端点：resourceId 形如 `P_PERF_METRIC_CREATE`、`P_PERF_KPI_PUBLISH`（角色绑定"绩效配置员"）
- 高危端点：resourceId 形如 `P_PERF_SYS_CONTROL_ROLLBACK`、`P_PERF_METRIC_DELETE`（角色绑定"绩效管理员" + `@AuditLog(reasonRequired=true)`）

架构测试：`BizAuthConsistencyArchTest` 守护此约束，任何 Controller 若使用其它 BizType 值将 CI 失败。

#### 7.1.1 PT_RESOURCE 资源 ID 与 @BizAuth action 完整对照表

以下对照表来自 V1_0_1 脚本（35 条启用资源）+ 实际 Controller 代码，共 35 条端点。

| Controller | HTTP 方法 + 路径 | @BizAuth.action | PT_RESOURCE RESOURCE_ID |
|---|---|---|---|
| MetricDefController | GET /api/perf/metrics | LIST | P_PERF_METRIC_LIST |
| MetricDefController | GET /api/perf/metrics/{metricCode} | READ | P_PERF_METRIC_GET |
| MetricDefController | POST /api/perf/metrics | WRITE | P_PERF_METRIC_ADD |
| MetricDefController | PUT /api/perf/metrics/{metricCode} | WRITE | P_PERF_METRIC_UPD |
| MetricDefController | DELETE /api/perf/metrics/{metricCode} | DELETE | P_PERF_METRIC_DEL |
| MetricDefController | PUT /api/perf/metrics/{metricCode}/status | CONFIG | P_PERF_METRIC_STAT |
| MetricDefController | GET /api/perf/metrics/{metricCode}/refs | READ | P_PERF_METRIC_REFS |
| MetricDefController | GET /api/perf/metrics/{metricCode}/ref-by | READ | P_PERF_METRIC_RBY |
| MetricDefController | GET /api/perf/metrics/val-slots | READ | P_PERF_METRIC_SLOT |
| MetricDefController | POST /api/perf/metrics/{metricCode}/slot/release | CONFIG | P_PERF_METRIC_SREL |
| KpiSchemeController | GET /api/perf/kpi-schemes | LIST | P_PERF_KPI_LIST |
| KpiSchemeController | GET /api/perf/kpi-schemes/{id} | READ | P_PERF_KPI_GET |
| KpiSchemeController | POST /api/perf/kpi-schemes | WRITE | P_PERF_KPI_ADD |
| KpiSchemeController | PUT /api/perf/kpi-schemes/{id} | WRITE | P_PERF_KPI_UPD |
| KpiSchemeController | DELETE /api/perf/kpi-schemes/{id} | DELETE | P_PERF_KPI_DEL |
| KpiSchemeController | POST /api/perf/kpi-schemes/{id}/publish | EXECUTE | P_PERF_KPI_PUB |
| KpiSchemeController | POST /api/perf/kpi-schemes/{id}/items | WRITE | P_PERF_KPI_IADD |
| KpiSchemeController | PUT /api/perf/kpi-schemes/{id}/items/{itemId} | WRITE | P_PERF_KPI_IUPD |
| KpiSchemeController | DELETE /api/perf/kpi-schemes/{id}/items/{itemId} | DELETE | P_PERF_KPI_IDEL |
| TargetPlanController | GET /api/perf/target-plans | LIST | P_PERF_TGT_P_LIST |
| TargetPlanController | GET /api/perf/target-plans/{id} | READ | P_PERF_TGT_P_GET |
| TargetPlanController | POST /api/perf/target-plans | WRITE | P_PERF_TGT_P_ADD |
| TargetPlanController | PUT /api/perf/target-plans/{id} | WRITE | P_PERF_TGT_P_UPD |
| TargetValueController | GET /api/perf/target-values | LIST | P_PERF_TGT_V_LIST |
| TargetValueController | POST /api/perf/target-values | WRITE | P_PERF_TGT_V_ADD |
| TargetValueController | POST /api/perf/target-values/batch | WRITE | P_PERF_TGT_V_BAT |
| AllocRelationController | GET /api/perf/alloc-relations | LIST | P_PERF_ALLOC_CUR |
| AllocRelationController | GET /api/perf/alloc-relations/history | READ | P_PERF_ALLOC_HIS |
| AllocRelationController | GET /api/perf/alloc-relations/summary | READ | P_PERF_ALLOC_SUM |
| PerfRunTaskController | GET /api/perf/run-tasks | LIST | P_PERF_RT_LIST |
| PerfRunTaskController | GET /api/perf/run-tasks/{id} | READ | P_PERF_RT_GET |
| SysControlController | GET /api/perf/sys-control | READ | P_PERF_SC_GET |
| SysControlController | GET /api/perf/sys-control/history | READ | P_PERF_SC_HIS |
| SysControlController | POST /api/perf/sys-control/init | CONFIG | P_PERF_SC_INIT |
| SysControlController | POST /api/perf/sys-control/switch-version | CONFIG | P_PERF_SC_SW |

> 注：所有端点 `bizType = BizType.PERF_CONFIG`（已统一）。V1.1/V1.2 新增的 10 条禁用资源见 V1_0_4 脚本，不在此表列出。

## 测试数据前缀约定

子代理并行开发时，每个子代理使用独立前缀避免冲突：
- P1-A SysControl: `TEST_SC_*` / `CONCUR_SC_*`
- P1-B Metric:    `TEST_METRIC_*` / `CONCUR_METRIC_*`
- P1-C Kpi:       `TEST_KPI_*`
- P1-D Target:    `TEST_TGT_*`
- P1-E RunTask:   `TEST_RT_*`
- P1-F Alloc:     `TEST_AR_*`

## 环境依赖

- MySQL 8.0 本地实例：`jdbc:mysql://localhost:3306/onepl`（root/123456）
- Redis 6.X 本地实例：`localhost:6379`
- 13 张 perf_* 表已在 onepl 库部署（来自 `docs/schema/ddl-performance.sql`）
- `pt_resource` 已注册 35 条 `P_PERF_*` 资源（V1_0_1 脚本）
- `sys_dict_item` 已注册 39 条 `PERF_*` 字典项（V1_0_2 脚本）

## 开发 Checklist（新增功能时）

1. ✅ 写失败的单元测试
2. ✅ 写最简实现让测试通过
3. ✅ 重构（保持测试通过）
4. ✅ 每步独立 commit
5. ✅ 所有 Controller 方法必标 `@BizAuth(bizType = BizType.PERF_CONFIG, action = ...)`
6. ✅ 写操作必标 `@AuditLog(action, resourceType)`，高危操作 `reasonRequired=true`
7. ✅ Service 层 public 写方法 `@Transactional(rollbackFor = Exception.class)`
8. ✅ Mapper XML 使用 `#{}` 不用 `${}`（除数据范围片段外）
9. ✅ 跨模块调用走对方 `*Api` 接口
10. ✅ 中文注释 + UTF-8 编码

## 相关文档

- Spec: `docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md`
- Plan: `docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md`
- 权威功能规格: `docs/modules/performance-engine-center/` (9 份)
- 对外 API 契约: `docs/modules/performance-engine-center/04-对外API契约.md`
- DDL 权威源: `docs/schema/ddl-performance.sql`
- 共通开发规范: `docs/common-dev-guide.md`

## 技术债务（V1.3 交付后）

本节记录 V1.3 交付后的技术债残量：V1.3 R0-R5 已消化 13 项大额债务，V1.4 新登记 8 项遗留（含 R6 WORKFLOW_PARTICIPANT 整体延期）。

### V1.3 已消化项（2026-04-24）

V1.3 R0-R5 共 18 个 Task 消化以下 13 项 V1.0-V1.2 累积技术债：

**DDL 与基础设施**：
- V1.0 NULL deleted 历史数据：V1.3 R0.1 通过 `V1_2_5__perf_cleanup_null_deleted.sql` 将 perf_target_value / perf_target_plan / perf_kpi_item 三表 NULL 置 0（兜底而非误删）。
- **perf_run_task.task_key UNIQUE KEY**：V1.3 R0.2 通过 `V1_3_0__perf_run_task_uk.sql` 补齐（V1.2 曾声称已加但实际未执行的勘误），配合 `DataTaskService.report` 的 DuplicateKeyException 降级路径，作为 Redis SETNX 幂等的 DB 兜底。
- failsafe 分层：V1.3 R0.3 pom.xml 新增 maven-failsafe-plugin，surefire 改为单元测试（549）+ failsafe 集成测试（339）分层，总 888 测试。
- 模块 CLAUDE.md uk_task_key 勘误：V1.3 R0.4 清理 V1.2 声称"已加但实际没做"的误述。

**Target 数据范围注入（V1.2 遗留#1 消化）**：
- V1.3 R1.1/R1.2/R1.3 在 TargetValue / TargetPlan 两表的 Service `pageWithScope` + Controller list 端点三级落地 PerfScopeHelper 注入（复用 V1.2 Q7 `ScopeColumns` 模式）。ScopeColumns Target 目前降级到 `created_by`，V1.4 引入 owner_emp_id / owner_org_code 字段后可细化。

**5 处 V1.2 Facade UOE 实际实现（V1.2 UOE 清零）**：
- `PerfCalcApi.triggerKpiCalc`（V1.3 R2.1）：委托 KpiCalcService.calcScheme，签名从 `(LocalDate) → String` 破坏性升级为 `(schemeCode, cycleType, cycleDate, asOfDate, version) → int`。
- `MetricQueryApi.batchQueryEmpSnapshots` / `batchQueryOrgSnapshots` / `batchQueryCustSnapshots`（V1.3 R2.2/R2.3/R2.4）：三个宽表 Mapper 批量查询，500 subject + 50 metricCode 上限。
- `MetricApi.getUserMetricCards`（V1.3 R2.5）：ACTIVE KPI 方案并集 + EMP 宽表 slot 读取 + TargetValue planId=null 近似查询。

**错误码与 DTO 细化**：
- IDEMPOTENCY_WAIT_TIMEOUT（CALC_JOB_FAILED 语义过载）：V1.3 R3.1 新增 `PERF-50003 IDEMPOTENCY_WAIT_TIMEOUT`，DataTaskService 替换原 `CALC_JOB_FAILED`。
- MetricTrialRespDTO 字段对齐 03 §A.5：V1.3 R3.2 将 `samples` 字段重命名为 `sampleRows`，`@JsonAlias({"samples"})` + `@Deprecated getSamples()` 兼容 1 版本（V1.5 清理）。
- execute 返回类型 Map → RunTaskInfoDTO：V1.3 R3.3 MetricDefController.execute 回归 03 §A.6 契约。

**Controller 与 Listener 收敛**：
- V1.2 遗留#6 其他 Controller 局部变量 entity：V1.3 R4.1 对 11 个 Controller（原 V1.2 计划 5 个，审查加码到 11 个）重构，DTO 装配下沉至 Facade/Service，新增架构守护 `NoEntityInControllerLocalsArchTest` 防回归。
- V1.2 遗留#7 P7 recalc 同步 RUNNING 状态：V1.3 R4.2 `/api/perf/recalc` Controller 响应 status 改读 `getRunTask(parentId)` 真实终态。
- V1.2 遗留#7 cycleType 仅审计字段透传：V1.3 R4.3 HistoryRecalcService 把 cycleType 写入 params_json，作为字段级审计留痕。

**测试基础设施**：
- V1.2 遗留#4 并发 IT 依赖本地 Redis：V1.3 R5.1 `PerformanceRedisTestBase` + Testcontainers-redis 接入，SysControlConcurrentIT + DataTaskServiceIdempotentIT 可在有 Docker 环境激活。
- V1.2 遗留#3 UndoScriptSmokeIT 过期：V1.3 R5.2 重写支持 V1_3_0 基线（R0.2 uk 补齐、R0.1 NULL 清理与原脚本共存）。
- V1.2 遗留#8 V1.0 UOE 测试资产：V1.3 R2 实施过程已将 MetricQueryApiImplTest / PerfCalcApiImplTest / MetricApiImplTest 的 UOE 断言替换为行为断言（Mock Service、入出参交互），R5.3 新增架构守护 `NoUoeInFacadeTestsArchTest` 防回归。

### V1.4 遗留项（已登记）

V1.3 仅做技术债清偿，未引入新业务能力。以下 8 项遗留项转入 V1.4 规划：

#### 1. WORKFLOW_PARTICIPANT scope 未落地（中，V1.3 R6 整体延期）

PerfScopeHelper Q7.1 已枚举 7 种 DataScopeType，`WORKFLOW_PARTICIPANT` 当前 fail-close 到 SELF 语义。
V1.3 R6 原计划落地但需 workflow-center 先提供 `WorkflowQueryApi.queryParticipatedBusinessKeys` 跨模块 API，
该 API 变更面大（工作流模块有独立 release cadence），整体转入 V1.4。

**V1.4 解决方向**：workflow-center 先加 `queryParticipatedBusinessKeys(empId)` 返回候选组 businessKey 集合，
PerfScopeHelper 再包一层 Resolver 组件透传到 SQL 过滤片段。

#### 2. MetricApi.getUserMetricCards mom/yoy 字段（中，V1.3 R2.5 简化）

V1.3 R2.5 交付的简化方案只填充 `target + actual`，`mom`（环比）/ `yoy`（同比）/ `previousValue` 字段留 null。
V1.4 需引入周期推导规则（MONTHLY 环比上月 / QUARTERLY 环比上季度 / YEARLY 同比上年）+ 读取历史版本
宽表数据。

#### 3. MetricTrialRespDTO @Deprecated getSamples() 1 版本后删除（低，V1.5 清理）

V1.3 R3.2 为保证前端兼容而保留 `@JsonAlias({"samples"}) getSamples()` getter，标 @Deprecated 1 版本后删除。
按"V1.3 → V1.4 → V1.5"的节奏，V1.5 删除该 getter 与 alias。

#### 4. MetricApi.getUserMetricCards cycleKey 按年口径近似（低，V1.3 R2.5 简化）

V1.3 R2.5 将 `cycleKey = latestDataDate.getYear()` 字符串化，对 MONTHLY/QUARTERLY 方案
可能与 perf_target_value 实际 cycleKey 不匹配。

**V1.4 解决方向**：按方案 cycleType 精确匹配（MONTHLY → `yyyyMM` / QUARTERLY → `yyyyQn` / YEARLY → `yyyy`）。

#### 5. ScopeColumns Target 降级到 created_by（低，V1.3 R1 简化）

V1.3 R1.1/R1.2 Target 侧 ScopeColumns 降级到 `created_by` 作为 owner 字段，未按 Kpi 的
`owner_emp_id` / `owner_org_code` 标准字段模型。

**V1.4 解决方向**：引入 `perf_target_plan.owner_emp_id` / `owner_org_code` 字段（DDL 变更），
ScopeColumns 切换到标准字段。

#### 6. V1.0/V1.1 Controller.list 返回类型签名（低，跨模块影响，V1.3 未改）

V1.0/V1.1 共 6 个 Controller（MetricDef / KpiScheme / TargetPlan / TargetValue / PerfRunTask / AllocAdjust / TargetAdjust）
的 list 方法均签名 `ResponseWrapper<XxxDTO>` + return `ResponseWrapper.page(PageResult<XxxDTO>)`。
该写法是 common-web `ResponseWrapper.page` 契约设计（ResponseWrapper 同时持有 data / page 两字段），
但"返回类型未直接反映分页语义"。需 common-web API 统一修改，跨模块影响面大，V1.3 不处理。

#### 7. MetricCalcApi execute fallback 日志（低，R4.2 reviewer 建议）

V1.3 R4.2 P7 recalc Controller 响应 status 从真实 `getRunTask(parentId)` 读取，
但底层 recalc 过程异常时，Controller 层现阶段未留 fallback 日志。

**V1.4 解决方向**：Controller 层加 try/catch + warn log，防止 recalc 异常时前端拿到误导性 RUNNING。

#### 8. cycleType=null 语义（低，R4.3 reviewer 建议）

V1.3 R4.3 `params_json` 写入 cycleType 时，未明确区分 `null` 与 `""`（空串）两种情况。
Facade 层可能透传任一形式，当前实现按 `null` 写入（jackson 会忽略字段）。

**V1.4 解决方向**：明确合约——null 不写字段，空串显式写 `{"cycleType": ""}`，或强制 cycleType 必填。

## 运维 Runbook（V1.2 + V1.3 交付）

### V1.3 启用前置检查（DDL 迁移安全门）

V1.3 引入两个关键 Flyway 脚本，生产启用前**必须**先做预检，防止 ALTER 失败阻塞后续迁移：

**1. V1_2_5__perf_cleanup_null_deleted.sql（V1.0 NULL deleted 历史数据清理）**

```sql
-- 预检：查看有多少历史行 deleted 为 NULL
SELECT COUNT(*) FROM perf_target_value WHERE deleted IS NULL;
SELECT COUNT(*) FROM perf_target_plan  WHERE deleted IS NULL;
SELECT COUNT(*) FROM perf_kpi_item     WHERE deleted IS NULL;
-- V1_2_5 会将这些 NULL 置为 0（未删除），等价于"兜底"而非"误删"
```

**2. V1_3_0__perf_run_task_uk.sql（uk_task_key 唯一键补齐）**

```sql
-- 预检：是否有重复 task_key 导致 ALTER 失败？
SELECT task_key, COUNT(*)
FROM perf_run_task
WHERE task_key IS NOT NULL
GROUP BY task_key
HAVING COUNT(*) > 1;
-- 有结果 → 手工清理重复后再跑 flyway:migrate
-- 无结果 → 直接跑 flyway:migrate
```

若 `V1_3_0` 迁移失败（存量重复）：
- (a) 按业务规则手工清理重复行；
- (b) 在 `flyway_schema_history` 删除 FAILED 对应行；
- (c) 重跑 `mvn flyway:migrate`。
- **严禁**直接跳到下个 V1_3_x 脚本，否则 schema 状态不一致。

配套 `DataTaskService.report` 已在 V1.3 R0.2 增加 `DuplicateKeyException` 降级路径（Redis 宕机时 DB 唯一键兜底）。

### 3 个定时任务默认关闭策略

V1.2 Q5 引入 3 个 `@Scheduled` 任务，均受 `perf.engine.enabled-jobs` 属性控制，**默认 OFF**
避免开发 / 测试环境误触发。生产启用步骤：

1. 在 `application-prod.yml` 追加：
   ```yaml
   perf:
     engine:
       enabled-jobs:
         daily-kpi-calc: true           # 每日 KPI 计算（原 Cron: 0 0 2 * * *）
         sys-control-cleanup: true      # 版本历史清理（Cron: 0 0 3 * * SUN）
         perf-run-task-cleanup: true    # 过期 run_task 清理（Cron: 0 0 4 * * *）
   ```

2. 确保 Redis 可用（ShedLock 依赖，无 Redis 时 Job 会跳过执行但不报错，仅单机状态）。

3. 生产启用后，首次触发前**必须**验证：
   - `sys_control` 已有有效 is_valid=1 基线版本（无则 DailyKpiCalcJob 会 warn + 跳过）
   - `perf_run_task` 的保留期策略（默认 90 天）符合审计要求

4. 紧急停止：
   - 修改 enabled-jobs → false 滚动重启服务；或
   - `flowable act_ru_job` 表手动删除 schedule

### 导出任务生命周期

- 成功/失败状态均保留 7 天（`perf_run_task` 自动过期清理）
- MinIO bucket: `perf-exports`, 文件 TTL: 3 天（MinIO 生命周期策略）
- 用户下载：通过 `GET /api/perf/export/task/{taskId}` 拿到 presigned URL（含 MinIO 1 小时签名）

### 事件总线消费者接入

V1.2 已发布 4 类领域事件到 Spring ApplicationEvent：
- `SysControlUpdatedEvent`（切版/回滚）
- `KpiCalcCompletedEvent`（KPI 计算完成）
- `TargetAdjustmentApprovedEvent`（目标调整审批通过）
- `AllocationAdjustmentApprovedEvent`（分配调整审批通过）

消费端通过 `@EventListener` 订阅，建议单独的 `@Async` 方法避免阻塞主流程。
事件字段契约见 `docs/modules/performance-engine-center/04-对外API契约.md` §10。
