<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# performance-engine-center

## Purpose

绩效计算中心（核心域），是 2026-04 末以来全仓库改动最活跃的模块。对外提供两大能力面：

1. **绩效配置与计算主干**：指标库（SQL/PROC/Groovy(EXPR)/SUMMARY 四种计算逻辑类型，PROC/SUMMARY 已声明枚举但未完全落地）、KPI 方案设计与计分、目标管理、客户分配关系、分配/目标调整审批（Flowable BPMN）、数据版本控制（`sys_control`）、Excel 导入（`ImportStrategy` 策略，8 个实现）、异步导出（`ExportStrategy` 策略，4 个实现）、指标级 Quartz 调度、统计展示表定期归档。
2. **eval 考核评价子域**（独立包 `eval/`）：规则驱动的评价任务（标签 `EVAL_TAG`/`EVAL_USER_TAG` → 规则 `EVAL_RULE`/`EVAL_RULE_GROUP` → 任务 `EVAL_TASK`/`EVAL_TASK_TARGET` → 打分 `EVAL_SCORE`），与之并行的第二条路径是手工导入的「待处理任务」——按 `importType` 分「评价任务导入」(EVAL) 与「奖励分配」(REWARD) 两种，落 `EVAL_ASSIGN_BATCH` + `EVAL_ASSIGN_ITEM`/`EVAL_REWARD_ITEM`，批次过期由 Quartz Job 自动关闭。两条路径互不影响，仅在列表层由 `EvalUnifiedService` 合并展示。

对外通过 11 个 `*Api`/`*QueryApi` 接口 + 约 30 个 Controller（19 个绩效主干 + 11 个 eval）/ 160+ REST 端点，服务其他模块（跨模块调用方按 `*Api` 接口约束）与外部渠道（soap-gateway-center callpu 网关，经 `PerfApprovalCmdApi`/`PerfApprovalQueryApi`/`CustStatQueryApi`）。

**基础包名**: `com.bank.branch.platform.performance`
**Maven 坐标**: `com.bank.branch.platform:performance-engine-center`

## Key Files

| File | Description |
|------|-------------|
| `api/MetricApi.java` / `api/MetricQueryApi.java` | 指标定义 CRUD + 快照批量查询（含 `getUserMetricCards` 的 mom/yoy 环比同比） |
| `api/KpiApi.java` / `api/PerfCalcApi.java` | KPI 方案管理与计分触发（`triggerKpiCalc` 在两个接口各有一个入口，签名不同） |
| `api/TargetApi.java` / `api/AllocApi.java` | 目标管理 / 客户分配关系查询 |
| `api/DataTaskApi.java` | 外部上报数据任务幂等入口 |
| `api/EvalQueryApi.java` | eval 只读查询（供 report-analytics-center 未来引用），实现见 `eval/facade/EvalQueryFacade.java` |
| `api/CustStatQueryApi.java` | 客户号查名只读接口，面向 soap-gateway-center callpu 网关 |
| `api/PerfApprovalCmdApi.java` / `api/PerfApprovalQueryApi.java` | 分配关系调整审批的外部渠道读写入口（手机端/callpu，权限与 Web 端一致） |
| `service/scope/PerfScopeHelper.java` | 数据范围注入（7 种 DataScopeType，含 fail-close 语义） |
| `service/MetricSchedulerService.java` | 指标级 Quartz 注册/反注册 + 启动同步 |
| `service/MetricSchedulerHealthCheck.java` | 10 分钟补偿扫描，兜底 KPI 事件驱动重算的 best-effort 丢失场景 |
| `job/quartz/*.java` | 4 个通用 Quartz `Job` 包装类（`implements Job`，不加 `@Component`），由 governance `JobService.syncJobsOnStartup` 按 `SYS_JOB_CONF.quartz_job_class` 反射建实例 + `AutowiringSpringBeanJobFactory` 注入 |
| `eval/service/EvalAssignImportService.java` / `eval/service/EvalRewardImportService.java` | 「待处理任务」两条异步导入管线（EVAL / REWARD），all-or-none |
| `eval/service/EvalUnifiedService.java` | 合并规则任务(`EVAL_TASK`)与导入批次(`EVAL_ASSIGN_BATCH`)的统一列表/删除 |
| `eval/job/EvalTaskExpireJob.java` / `eval/job/EvalAssignBatchExpireJob.java` | 同样走 `SYS_JOB_CONF` 声明式注册的过期任务/批次自动关闭 Job |
| `enums/PerfErrorCode.java` | 63 个 `PERF-*` 错误码（含 eval 子域） |
| `exception/PerfException.java` | 模块统一业务异常（extends common-web `BizException`） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `api/` + `api/dto/` + `api/dto/cmd/` | 11 个对外 Api 接口 + DTO/Cmd（唯一允许跨模块依赖的包） |
| `controller/` + `controller/dto/` | 绩效计算主干 REST 控制器（19 个）+ 请求/响应 DTO |
| `eval/` | 考核评价子域，自成一套 `controller/`（11 个）/`service/`/`mapper/`/`entity/`/`dto/`/`facade/`/`job/` 子包，与主干包结构并列但物理隔离 |
| `facade/` + `facade/assembler/` | 11 个 Api 实现（`*ApiImpl`/`*Facade`）+ 6 个 DTO 装配器；Redis 分布式锁在此层申请/释放 |
| `service/` | 主干业务逻辑（约 28 个 Service 类：指标计算/KPI/目标/分配/调整/统计展示等） |
| `service/engine/` | Groovy/SQL 计算引擎：`GroovyExecutorImpl`、`SqlExecutorImpl`、`MetricRefTokenParser`（引用指标 token 解析）、`SafeDivClosure`（Groovy 安全除法，除零/异常兜底返回 0）、`DateMacroResolver` |
| `service/importer/` + `impl/` + `model/` | `ImportStrategy` 策略接口，8 个实现（METRIC_DEF/KPI_SCHEME/TARGET_PLAN/BASE_DATA/ALLOC/KPI_SCORE/METRIC_RESULT 等），`ImportContext` 透传跨策略上下文（如 `dataDate`） |
| `service/export/` + `impl/` + `model/` | `ExportStrategy` 策略接口，4 个实现，异步任务 + MinIO |
| `service/adjust/` + `cmd/` | 分配/目标调整审批业务（Flowable BPMN 集成） |
| `service/scope/` | `PerfScopeHelper` 数据范围注入 |
| `service/result/` | 指标定义批量 upsert 结果值对象 |
| `mapper/` + `mapper/typehandler/` | MyBatis Mapper（32 个，含 3 张宽表 Mapper + `SlotMapTypeHandler` 自定义类型处理器） |
| `entity/` | 贫血模型（22 个，含 3 张宽表 Entity） |
| `enums/` | 枚举 + `PerfErrorCode`（63 个 `PERF-*` 错误码） |
| `event/` + `listener/` | 4 类领域事件（Spring `ApplicationEvent`）+ 3 个监听器；`KpiCascadeListener` 事件驱动 KPI 重算 |
| `job/` + `job/quartz/` | 业务 Job（裸方法，如 `Level1/2/3MetricCalcJob`、`KpiScoreCalcJob`）+ Quartz `Job` 包装类，全部走 `SYS_JOB_CONF` 声明式注册 |
| `config/` | Spring 配置（自动装配/MyBatis/Redis/BPMN 部署/eval 导入线程池/KPI 级联异步线程池/`@EnableScheduling`） |
| `exception/` | `PerfException` |

## For AI Agents

### Working In This Directory
- 严格 TDD 红-绿-重构闭环，每步独立 commit；`eval` 子域近期提交（`test(eval): ...`「红」→ `feat(eval): ...`「绿」成对出现）是范例节奏，照此模式改动。
- **新增数据库访问一律走 MyBatis-Plus**（2026-06-10 起仓库红线，见根 CLAUDE.md）：Mapper `extends BaseMapper<T>`，单条 CRUD 用内置方法，XML 只写聚合/批量插入/JOIN/带乐观条件的批更新等自定义 SQL；`eval` 子域（如 `EvalRewardItemMapper`）是该规范的示范实现，仿写时以它为准而非主干旧 Mapper。
- Controller 方法必标 `@BizAuth`，**但 `bizType` 分裂为两套体系，改动前务必确认自己在哪个包**：
  - `controller/`（绩效计算主干）：只能是 `BizType.PERF_CONFIG` 或 `BizType.KPI_CALC`（KPI 结果详情/导出用 `KPI_CALC`），由 `src/test/.../arch/BizAuthConsistencyArchTest.java` 守护——**该架构测试 `@AnalyzeClasses` 仅扫描 `performance.controller` 包，不覆盖 `performance.eval.controller`**，不要误以为它也在管 eval。
  - `eval/controller/`：统一用 `BizType.EVAL`，无同类架构测试约束；新增 eval 端点时不要机械套用 `PERF_CONFIG`。
- 跨模块调用一律走对方 `*Api`，禁止直连 `mapper`/`entity`/`serviceImpl`。当前实际依赖（以 `pom.xml`/`import` 为准，比只看根 CLAUDE.md 依赖图文字描述更可靠）：auth（`CurrentUserApi`/`BizScopeApi`/`OrgApi`/`RoleApi`/`UserApi`）、governance（`DictApi`/`FileApi`/`NotifyApi`/`AuditApi`/`JobApi`）、workflow（`WorkflowApi`/`WorkflowQueryApi`/`TodoQueryApi`）、**portal-content-center 的 `AddressBookApi`**（指标结果导入的员工存在性校验）、**customer-marketing-center 的 `CustomerQueryApi`**——后两者未出现在根 CLAUDE.md 模块依赖图的文字描述里，是实际代码已引入但文档滞后的部分。
- 错误码前缀统一 `PERF-{HTTP_STATUS}{SEQ}`，当前 63 个，登记在 `enums/PerfErrorCode.java`；新增前先检索该文件避免编号冲突（eval 子域近期新增集中在 `PERF-400xx` 段）。
- 架构守护共 6 个（均在 `src/test/.../arch/` 包）：`BizAuthConsistencyArchTest`、`NoEntityInControllerArchTest` / `NoEntityInControllerLocalsArchTest`（Controller 局部变量禁用 entity，DTO 装配下沉到 Facade/Service）、`NoOldDailyKpiCalcArchTest`（防已删除的 `DailyKpiCalcJob` 类回潮）、`NoUoeInFacadeTestsArchTest`（facade 测试层禁写 `assertThrows(UnsupportedOperationException.class, ...)`）、`NoV11UOEArchTest`。改动前先跑一遍这些测试，比通读历史变更日志更快确认是否会撞红线。

### Testing Requirements
- 测试目录与 `src/main/java` 包结构镜像；`support/PerfTestConfig` 是公共 mock bean 装配点——新引入的外部 `*Api` 依赖必须先在此加 mock，否则相关测试的 Spring 上下文加载会直接失败。
- 单线程 Mapper IT 继承 `PerformanceMapperTestBase`（含 `@Transactional`）；并发场景改继承 `PerformanceConcurrentTestBase`（**不含** `@Transactional`）+ `TestDbCleaner` + 独立前缀隔离（如 `TEST_SC_*`/`TEST_METRIC_*`/`TEST_KPI_*`/`TEST_TGT_*`/`TEST_RT_*`/`TEST_AR_*`），原因是 `@Transactional + @Rollback` 与多线程测试的线程本地事务绑定不兼容。
- Redis 相关 IT 用 Testcontainers-redis（`pom.xml` 已声明 `spring-boot-starter-data-redis` + `testcontainers-redis`）。
- `*Test.java` 走 surefire（`mvn test` 触发），`*IT.java` 走 failsafe（`mvn verify` 触发，`mvn test` 不跑）；跨模块改动后按根 CLAUDE.md「Stale jar 处理」章节先 `mvn clean install -DskipTests` 再跑 IT，否则可能加载到旧 jar 里的旧类。

### Common Patterns
- 配置表 Redis 缓存 + `TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit` 回调触发 evict，避免事务未提交时的脏数据污染缓存。
- Redis 分布式锁在 **Facade 层**申请/释放（如 `SysControlFacade.switchVersion`），Service 层保持 `@Transactional`——Spring 事务方法内无法在"事务外"安全持锁，这是本模块固定分层约定。
- **调度已统一收敛到 Quartz 集群**（`isClustered=true` + JDBC JobStore，防重靠 `QRTZ_LOCKS` 行锁；ShedLock 已彻底删除，不要再引入）。两种注册方式并存：
  - 通用固定 Job（`SysControlCleanupQuartzJob`/`PerfRunTaskCleanupQuartzJob`/`StatShowArchiveQuartzJob`/`EvalTaskExpireJob`/`EvalAssignBatchExpireJob`）：`SYS_JOB_CONF` 静态配一行，governance `JobService.syncJobsOnStartup` 启动期同步。
  - 指标级动态 Job（`MetricExecuteQuartzJob`）：每条 ACTIVE+AUTO 指标 1:1 注册，`jobKey="PERF_METRIC_${metricCode}"`，由 `MetricSchedulerService` 在指标 CRUD 的 `afterCommit` Hook 里调 governance `JobApi.registerJob`/`unregisterJob` 动态增删。
  - **所有 Job 包装类都直接 `implements Job` 且不加 `@Component`**——Quartz 反射 `newInstance()` 建实例，`AutowiringSpringBeanJobFactory`（governance `QuartzConfig` 持有）完成 `@Autowired` 注入。**磁盘上已不存在 `PerfQuartzConfig` 类**（早期基于 `QuartzJobBean` + 本模块专属配置类静态注册 JobDetail/Trigger 的写法已被上述声明式模式取代），若历史文档提到它，以当前代码为准。
- KPI 重算是**事件驱动**而非定时轮询：`MetricCalcCompletedEvent` → `KpiCascadeListener`（`@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + Redis `SETNX` 30s 防重）；该链路是 best-effort（事件 publish 之前 JVM 崩溃则丢失，永不重算），兜底靠 `MetricSchedulerHealthCheck` 每 10 分钟扫描补偿。
- 导入统一走 `ImportStrategy` 接口 + `POST /api/perf/import/upload` 单一入口，按 `importType` 分派。多数策略是「行级最大努力」（单行错误累计到 `errorSummary`，不影响其它行入库），少数（如 `METRIC_DEF`）是「整批 all-or-none」——改动前先看目标策略类头部 javadoc 确认是哪种语义，不要凭直觉套用另一种。
- 导出统一走 `ExportStrategy` 接口 + 异步任务落 `perf_run_task`，产物存 MinIO（`perf-exports` bucket，presigned URL 下载）。
- eval 子域的「待处理任务」（`EVAL_ASSIGN_BATCH`/`EVAL_ASSIGN_ITEM`/`EVAL_REWARD_ITEM`）是独立于规则驱动 `EVAL_TASK`/`EVAL_SCORE` 之外的第二条数据路径，两者物理隔离、互不影响；`EvalUnifiedService`/`EvalUnifiedController` 只在展示层做列表合并与统一删除，不做写路径合并，新增业务规则时不要试图把两条路径的表结构或校验逻辑合一。
- eval 人员标签模型是「单一角色化」：`EVAL_USER_TAG` 每人至多一个标签（`UK_USER(USER_ID)`），「被评价/评价」方向不存在于人员侧，完全由规则承载（标签出现在 `EVAL_RULE.be_eval_tag_id` = 被评价，出现在 `EVAL_RULE_GROUP.eval_tag_id` = 评价人）；改这块逻辑前先确认不是在找一个已被删除的 `role_type`/`tag_type` 字段（历史迭代已两度收窄，痕迹可能仍留在旧文档或注释里）。

## Dependencies

### Internal
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- `auth-permission-center`：`CurrentUserApi` / `BizScopeApi` / `OrgApi` / `RoleApi` / `UserApi`
- `system-governance-center`：`DictApi` / `FileApi` / `NotifyApi` / `AuditApi` / `JobApi`（含 Quartz 集群基础设施 `QuartzConfig` / `JobExecutionLogger` / `syncJobsOnStartup`）
- `workflow-center`：`WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi`
- `portal-content-center`：`AddressBookApi`
- `customer-marketing-center`：`CustomerQueryApi`

### External
- MyBatis 3.0.3 + MyBatis-Plus（`mybatis-plus-spring-boot3-starter`）— ORM，新代码一律按 MyBatis-Plus 规范
- Flowable 7.0.1 — 工作流引擎（通过 workflow-center，分配/目标调整审批走 BPMN）
- Quartz（`spring-boot-starter-quartz`）— 集群调度，`isClustered=true` + JDBC JobStore
- 缓存：JVM 内存（`PerformanceRedisConfig` 的 `ConcurrentMapCacheManager`，2026-05 去 Redis 后替代 RedisCacheManager，@Cacheable 注解不变）；`spring-boot-starter-data-redis` 仅 test scope 供遗留并发 IT（ShedLock 已删除）
- Groovy — 指标计算表达式引擎
- EasyExcel + `commons-compress` — Excel 导入导出
- MinIO — 导出文件存储（通过 governance `FileApi`）
- Testcontainers-redis — Redis 集成测试

## Database Tables（约 30 张）

**绩效计算主干**（19 张）：`SYS_CONTROL` / `PERF_METRIC_DEF` / `PERF_METRIC_REF` / `PERF_KPI_SCHEME` / `PERF_KPI_ITEM` / `PERF_KPI_SCORE` / `PERF_KPI_CALC_LOG` / `PERF_TARGET_PLAN` / `PERF_TARGET_VALUE` / `CUST_ALLOC_RELATION` / `PERF_RUN_TASK` / `PERF_IMPORT_BATCH` / `PERF_ALLOC_ADJUST_APPLY` / `PERF_ALLOC_ADJUST_ITEM` / `PERF_TARGET_ADJUST_APPLY` / `EMP_INDEX_RESULT` / `ORG_INDEX_RESULT` / `CUST_INDEX_RESULT` / `KPI_RESULT`

**eval 考核评价子域**（11 张）：`EVAL_TAG` / `EVAL_USER_TAG` / `EVAL_USER_SETTING` / `EVAL_RULE` / `EVAL_RULE_GROUP` / `EVAL_TASK` / `EVAL_TASK_TARGET` / `EVAL_SCORE` / `EVAL_ASSIGN_BATCH` / `EVAL_ASSIGN_ITEM` / `EVAL_REWARD_ITEM`

> DDL 权威源：`docs/schema/ddl-performance.sql`（绩效主干基线）+ `docs/superpowers/sql/*eval*.sql`（eval 子域各表增量脚本，按文件名日期顺序在目标库手工执行）。本项目已废弃 Flyway，schema 变更一律手工执行 SQL，禁止新增任何版本化自动 migrate 依赖（详见根 CLAUDE.md「Flyway 禁令」）。

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
