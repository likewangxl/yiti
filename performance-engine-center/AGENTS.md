<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-04-29 -->

# performance-engine-center

## Purpose
绩效计算中心（核心域），提供指标库管理、KPI 方案设计、目标管理、客户分配关系查询、数据版本控制、调整审批流程、异步导出、数据范围注入、Quartz 集群调度等能力。

**基础包名**: `com.bank.branch.platform.performance`
**Maven 坐标**: `com.bank.branch.platform:performance-engine-center`
**对外契约**: 7 个 `*Api` 接口 + 35 个 REST 端点。
**当前版本**: V1.6（quartz 整合，2026-04-25 交付）

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/performance/api/MetricApi.java` | 指标定义 API（7 方法） |
| `src/main/java/com/bank/branch/platform/performance/api/MetricQueryApi.java` | 指标快照查询 API（3 方法） |
| `src/main/java/com/bank/branch/platform/performance/api/KpiApi.java` | KPI 方案 API（5 方法） |
| `src/main/java/com/bank/branch/platform/performance/api/TargetApi.java` | 目标管理 API（4 方法） |
| `src/main/java/com/bank/branch/platform/performance/api/PerfCalcApi.java` | 绩效计算 API（3 方法） |
| `src/main/java/com/bank/branch/platform/performance/api/DataTaskApi.java` | 数据任务 API（1 方法） |
| `src/main/java/com/bank/branch/platform/performance/api/AllocApi.java` | 分配关系 API（10 方法） |
| `src/main/java/com/bank/branch/platform/performance/service/PerfScopeHelper.java` | 数据范围注入（7 种 DataScopeType） |
| `src/main/java/com/bank/branch/platform/performance/config/PerfQuartzConfig.java` | V1.6 注册 3 个 Quartz JobDetail + Trigger |
| `src/main/java/com/bank/branch/platform/performance/job/*QuartzJob.java` | V1.6 3 个 QuartzJobBean 包装类（DailyKpiCalc / SysControlCleanup / PerfRunTaskCleanup） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `api/` | 7 个对外 API 接口 + 14 个 DTO + 1 个 Cmd |
| `controller/` | 8 个 REST 控制器（35 端点） |
| `facade/` | 7 个 API 实现 + 分布式锁 |
| `service/` | 业务逻辑（含 MetricCalcService / KpiCalcService / PerfImportService / HistoryRecalcService） |
| `mapper/` | MyBatis Mapper 接口（含 *IndexResult / KpiResult 宽表 Mapper） |
| `entity/` | 贫血模型（含 *IndexResult / KpiResult 宽表 Entity） |
| `enums/` | 枚举 + 30 个 PERF-* 错误码 |
| `listener/` | 事件监听器 |
| `config/` | Spring 配置（AutoConfig / MyBatis / Redis） |

## For AI Agents

### Working In This Directory
- 严格 TDD 红-绿-重构闭环，每步独立 commit
- 所有 Controller 方法必标 `@BizAuth(bizType = BizType.PERF_CONFIG, action = ...)`
- 跨模块调用走对方 `*Api` 接口
- 错误码前缀 `PERF-{HTTP_STATUS}{SEQ}`
- 架构守护: `NoEntityInControllerLocalsArchTest` / `NoUoeInFacadeTestsArchTest` / `BizAuthConsistencyArchTest` 等 6 个

### Testing Requirements
- surefire 580 + failsafe 354 = 934 全绿
- 测试数据使用独立前缀（`TEST_SC_*` / `TEST_METRIC_*` / `TEST_KPI_*` 等）
- 并发 IT 使用 `PerformanceConcurrentTestBase`（不含 @Transactional）+ `TestDbCleaner` + 前缀隔离
- Redis IT 使用 Testcontainers-redis

### Common Patterns
- 配置表 Redis 缓存 + afterCommit evict 防脏读
- Redis 锁在 Facade 层申请/释放，Service 层 @Transactional
- 3 个业务 Job 由 Quartz 集群调度（V1.6 整合后），cron 配置走 `sys_job_conf` 表，启动期 `JobService.syncJobsOnStartup` 同步到 QRTZ_*；防重由 `QRTZ_LOCKS` 行锁接管（不再依赖 ShedLock）
- 导出任务同步执行（对齐 PerfExport V1.2 模型），V1.1+ 切异步

## Dependencies

### Internal
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- `auth-permission-center`（CurrentUserApi / BizScopeApi / OrgApi）
- `system-governance-center`（DictApi / FileApi / NotifyApi / AuditApi / CalendarApi / JobApi — V1.6 后 JobApi 精简到 1 方法 getJobConf）
- `workflow-center`（WorkflowApi + WorkflowQueryApi — V1.4 新增 WORKFLOW_PARTICIPANT 查询）

### External
- MyBatis 3.0.3 — ORM
- Flowable 7.0.1 — 工作流引擎（通过 workflow-center）
- Quartz — 集群调度（V1.6 引入，governance 持有 SchedulerFactoryBean + JobExecutionLogger）
- Redis 6.X — 缓存（ShedLock 已删除）
- MinIO — 导出文件存储（通过 governance FileApi）

## Database Tables (13 张)

**配置表** (6): `sys_control` / `perf_metric_def` / `perf_metric_ref` / `perf_kpi_scheme` / `perf_kpi_item` / `perf_target_plan`

**业务数据表** (2): `perf_target_value` / `cust_alloc_relation`

**日志表** (1): `perf_run_task`

**宽表** (4): `emp_index_result` / `org_index_result` / `cust_index_result` / `kpi_result`

## Version History

| Version | Scope | Status |
|---------|-------|--------|
| V1.0 | 配置态 CRUD + 版本管理骨架 + 7 个 Api 契约定型 | 已交付 |
| V1.1 | 指标执行（SQL+Groovy）、KPI 计算、数据导入、历史回算 | 已交付 |
| V1.2 | 审批 BPMN + 4 类事件 + 4 导出策略 + ShedLock + PerfScopeHelper + 45 资源激活 | 已交付 |
| V1.3 | 技术债清偿：DDL 兜底 + Target 数据范围 + 5 处 UOE 实现 + 11 Controller entity 清零 + Testcontainers | 已交付 |
| V1.4 | WORKFLOW_PARTICIPANT 真实查询 + Target owner DDL + mom/yoy 计算 | 已交付 |
| V1.5 | @Deprecated 删除 + cycleType 空串 + 分组修复 + batch 宽表 + yoy WEEKLY + owner <if> | 已交付（2026-04-24） |
| V1.6 | quartz 整合：governance Quartz 基础设施 + 3 Job 删 @Scheduled/@SchedulerLock + 3 个 QuartzJobBean 包装 + PerfQuartzConfig 注册 + ShedLock 全部痕迹删除 + JobApi 精简到 1 方法 | 已交付（2026-04-25） |

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
