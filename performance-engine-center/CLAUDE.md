# performance-engine-center/ CLAUDE.md

本文件为 `performance-engine-center` 模块提供上下文说明。

## 模块概述

**performance-engine-center** 是绩效计算中心（核心域），为整个平台提供指标库管理、KPI 方案设计、目标管理、客户分配关系查询、数据版本控制、调整审批流程、异步导出、数据范围注入等能力。

**当前版本**: V1.2（流程 + 事件 + 导出 + 数据范围）—— 在 V1.1 指标/KPI/导入/回算全线之上补齐：
- Q1 版本回滚 + SysControlCleanup
- Q2/Q3 分配/目标调整审批（BPMN + 事件监听 + Facade 编排）
- Q4 4 类领域事件发布（SysControlUpdated / KpiCalcCompleted / TargetAdjustmentApproved / AllocationAdjustmentApproved）
- Q5 ShedLock 分布式锁 + 3 Job 接入
- Q6 4 导出策略（KPI / Metric / Alloc / Detail）+ 异步导出框架
- Q7 PerfScopeHelper 7 种 DataScopeType + fail-close + AllocRelation/Kpi/Metric 数据范围注入
- Q8 surefire 假绿修复 + PT_RESOURCE 资源全量激活 + 业务种子数据

**基础包名**: `com.bank.branch.platform.performance`
**Maven 坐标**: `com.bank.branch.platform:performance-engine-center`

**Spec**: `docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md`（v1.2）
**Plan**: `docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md`

## 分期策略

| 版本 | 范围 | 状态 |
|---|---|---|
| V1.0 | 配置态 CRUD + 版本管理骨架 + 只读查询 + 7 个 Api（契约定型）| 已交付 |
| V1.1 | 指标执行（SQL+Groovy+级联）、KPI 计算（定时任务+手动触发）、数据导入（Excel/SQL/外部上报 3 策略）、历史回算（父子 run_task） | 已交付 |
| **V1.2** | 分配/目标调整审批（BPMN + Flowable）、4 类领域事件发布、4 导出策略（异步任务 + MinIO）、ShedLock 分布式锁、PerfScopeHelper 数据范围注入、PT_RESOURCE 资源全量激活（45 条） | **本期交付（2026-04-24）** |
| V1.3 | Target 数据范围注入、WORKFLOW_PARTICIPANT scope 落地、Testcontainers-redis 接入、剩余 UOE（MetricApi.getUserMetricCards 等）、Controller.list 返回类型泛化（V1.0/V1.1 Controller 系统性瑕疵） | 规划中 |

### V1.2 UOE 清单交付说明（Facade 5 方法）

| Api.方法 | V1.2 状态 | 说明 |
|---|---|---|
| `MetricApi.getUserMetricCards` | 延期 V1.3 | 仍抛 UOE("V1.2 delivered" 已改为正确的延期 message)。依赖 KPI 方案-目标-实绩联动 + 同比/环比展示层聚合，V1.2 未完整覆盖。 |
| `MetricQueryApi.batchQueryEmpSnapshots` | 延期 V1.3 | report-analytics 专用批量快照，500 条上限语义需 ScopeHelper 细化后启用。 |
| `MetricQueryApi.batchQueryOrgSnapshots` | 延期 V1.3 | 同上 |
| `MetricQueryApi.batchQueryCustSnapshots` | 延期 V1.3 | 同上 |
| `PerfCalcApi.triggerKpiCalc` | 契约冗余 | KpiApi.triggerKpiCalc 已于 V1.1 交付，PerfCalcApi 侧的同名方法保留为契约占位避免破坏 04 契约文档。Facade 实现抛 UOE 但 CLI 不会调用该入口。|

架构测试 `NoV11UOEArchTest` 守护：facade/*.java 不得再出现 `"V1.1 delivered"` 字面量（message 统一为 `"V1.2 delivered"`）。

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
│   ├── PerfErrorCode.java      (29 个 PERF-* 错误码：V1.0 25 个 + V1.1 新增 4 个：42206 批量上限 / 40005 KPI 方案重复 / 40006 目标方案重复 / 40007 RunTask 不存在)
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

### 2. 7 个对外 Api 契约 V1.0 定型, V1.1 替换实现, 剩余 V1.2 UOE 占位

V1.0 的 7 个对外 Api 签名全部按 `docs/modules/performance-engine-center/04-对外API契约.md` 定型。
V1.0 无法实现的 13 个方法原抛 `UnsupportedOperationException("V1.1 delivered")` 占位；
V1.1 交付后，实际落地 9 个方法（MetricApi 3 + KpiApi 2 + PerfCalcApi 2 + DataTaskApi 1 + 内部补充 1），
剩余 5 个保留 UOE 占位但 message 已改为 `"V1.2 delivered"`：
- `MetricApi.getUserMetricCards`（依赖 V1.2 的 KPI 方案绑定 + 目标/实绩联动 + 同环比）
- `MetricQueryApi.batchQueryEmpSnapshots / batchQueryOrgSnapshots / batchQueryCustSnapshots`（report-analytics 专用大批量快照）
- `PerfCalcApi.triggerKpiCalc`（契约冗余：V1.1 已在 KpiApi.triggerKpiCalc 交付）

架构测试 `NoV11UOEArchTest` 守护：facade/*.java 不得再出现 `"V1.1 delivered"` 字面量。

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

## 技术债务（V1.2 交付后）

本节记录 V1.2 交付后遗留的技术债，将在 V1.3 迭代时逐项消化。

### V1.2 已消化项（2026-04-24）

**V1.1 遗留项全部或部分消化**：
- childTaskIds 持久化：V1.1 P8.C.1 已在双循环后写 `updateResultPreviewJson(parentTaskId, JSON)`，消费方可据此读出子任务 ID 列表。
- 错误码语义归并：V1.1 P8.A 已落地 `PERF-40005/40006/40007`，`KpiSchemeService.create` / `TargetPlanService.create` / `PerfRunTaskController.getById` 抛错点已迁移。
- `MetricTrialRespDTO` 字段命名：Q3 执行期 MetricAssembler 字段兼容性校验通过。
- **perf_run_task.task_key UNIQUE KEY**：V1.3 R0.2 通过 `V1_3_0__perf_run_task_uk.sql` 补齐（V1.2 曾声称已加但实际未执行，V1.3 勘误）。配合 DataTaskService.report 的 DuplicateKeyException catch 分支，作为 Redis SETNX 幂等的 DB 兜底。
- 04 契约文档 `reportDataTaskStatus` void 签名：V1.2 Q8.2 已同步改为 `DataTaskReportResultDTO`。

**V1.2 Q8 收尾消化项**：
- surefire 假绿（47+ IT 历史不被扫描）：Q8.3 pom.xml 新增 `<include>**/*IT.java</include>`，测试数从 519 → 842。
- AllocRelationControllerIT action bug：Q8.4 list 断言 READ → LIST 对齐 Controller。
- V1.0 历史 Controller IT 错误码断言过期：Q8.5a 全部对齐 PerfErrorCode §K 权威清单。
- MetricDefControllerIT dataset 污染 + MetricDefService.create deleted=0 漏设置：Q8.5b 已修。
- V1.0/V1.1 遗留 PT_RESOURCE 规划资源未激活：Q8.6 V1_2_4 已激活 7 条（METRIC_EXEC / METRIC_TRIAL / KPI_TRIGGER / DTASK_STATUS / ALLOC_ADJ_ADD / KPI_RECALC / SC_ROLLBACK），全量 45 条启用。
- V1.2 业务种子数据缺失：Q8.1 V1_2_3 已预置 5 指标 + 2 KPI 方案 + 1 目标方案。

### V1.2 遗留项（留 V1.3 消化）

#### 1. Target 数据范围注入未落地（中）

Q7.3 已完成 AllocRelation + Kpi + Metric 3 处数据范围示例，但 Target 侧（TargetPlan / TargetValue）
仍由 Service 直接 Mapper，未经 PerfScopeHelper 注入。

**解决方向**：V1.3 参照 Kpi 的示范（PerfScopeHelper.applyScope + *ScopeIntegrationTest）
补全 TargetPlanService.list / TargetValueService.list 的数据范围注入。

#### 2. WORKFLOW_PARTICIPANT scope 未落地（低）

PerfScopeHelper 在 Q7.1 已枚举 7 种 DataScopeType，但 `WORKFLOW_PARTICIPANT` 目前 fall-back 到
SELF 语义（实际 fail-close 路径），原因：需查询 Flowable act_ru_identitylink 得出候选组 empId 集合，
跨域查询性能未评估。

**解决方向**：V1.3 引入 WorkflowParticipantResolver 协作接口，经 workflow-center 提供
`resolveParticipantScope(bizType)` 返回可见 empId/orgCode 集合，再由 PerfScopeHelper 透传到 SQL 片段。

#### 3. UndoScriptSmokeIT 过期（低）

V1.0 登记的 UndoScriptSmokeIT 基线是 V1_0_3 终态。V1.1/V1.2 新增版本后，flyway.migrate()
会迁移到最新版，断言失效。V1.2 Q8.5c 已标 @Disabled 并登记取消条件。

**解决方向**：V1.3 重写为"只针对 V1_0_3 /V1_0_4 的局部 undo 验证"，或接入 Flyway Teams 原生 undo API。

#### 4. 并发 IT 依赖本地 Redis（低）

DataTaskServiceIdempotentIT 和 SysControlConcurrentIT 要求 localhost:6379 运行。
V1.2 Q8.5c 已标 @Disabled。

**解决方向**：V1.3 接入 Testcontainers-redis（parent pom 已引入 testcontainers-bom），
@BeforeAll 启动 Redis 容器并动态注入 spring.data.redis.host。

#### 5. V1.0/V1.1 Controller.list 返回类型签名（低）

V1.0/V1.1 共 6 个 Controller（MetricDef / KpiScheme / TargetPlan / TargetValue / PerfRunTask / AllocAdjust / TargetAdjust）
的 list 方法均签名 `ResponseWrapper<XxxDTO>`（元素类型）+ return `ResponseWrapper.page(PageResult<XxxDTO>)`。
这本是 common-web `ResponseWrapper.page` 的契约设计（ResponseWrapper 内部同时持有 data / page 两字段），
V1.2 Q8.5d 曾误判为瑕疵，实际无需修改——已验证撤销。

若后续希望"返回类型直接反映分页语义"，需统一修改 common-web 的 ResponseWrapper API 契约，
非单模块改动，暂不处理。

#### 6. 其他 Controller 局部变量 entity（低，V1.1 遗留）

5 个 Controller（KpiScheme / TargetPlan / TargetValue / AllocRelation / PerfRunTask）的方法体内
仍直接使用 entity 作为 Service 返回值接收中间变量。DTO 装配分散在 Controller 层。

**解决方向**：V1.3 重构为 Facade 层统一 DTO 装配，Controller 只做入参校验和响应封装。

#### 7. P7 回算接口遗留项（低，V1.1 遗留）

- `PerfCalcApi.triggerRecalc(5 参数)` 当 `from>to` 或 `metricCodes` 包含不存在的 code 时异常传播路径未显式覆盖，依赖 `HistoryRecalcService` 兜底抛 `PerfException`。
- `cycleType` 参数当前在 Facade 层仅作审计字段透传，Service 层按日切分。
- 同步返回的父 `run_task` 状态在 `recalc()` 返回瞬间为 `RUNNING`，消费方需通过 `getRunTask(parentId)` 轮询或订阅 V1.2 已发布的 4 类事件。

#### 8. V1.0 UOE 测试资产（低，V1.0 遗留）

`MetricQueryApiImplTest.batchQuery*Snapshots_throwsUoe` / `PerfCalcApiImplTest.triggerKpiCalc_throwsUOE`
目前均断言 UOE + `"V1.2 delivered"` 消息。V1.3 真正交付时需把这些测试替换为行为断言
（Mock Service、验证入参/出参/交互）。

## 运维 Runbook（V1.2 交付）

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
