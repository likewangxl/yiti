# performance-engine-center/ CLAUDE.md

本文件为 `performance-engine-center` 模块提供上下文说明，是本模块开发指导的唯一权威来源（同目录 `AGENTS.md` 仅存指针，不再承载内容）。

## 模块概述

**performance-engine-center** 是绩效计算中心（核心域），对外提供两大能力面：

1. **绩效配置与计算主干**：指标库（SQL/PROC/Groovy(EXPR)/SUMMARY 四种计算逻辑类型）、KPI 方案设计与计分、目标管理、客户分配关系、分配/目标调整审批（Flowable BPMN）、数据版本控制（`sys_control`）、Excel 导入（`ImportStrategy` 策略）、异步导出（`ExportStrategy` 策略）、指标级 Quartz 调度。
2. **eval 考核评价子域**（独立包 `eval/`）：规则驱动的评价任务（标签 → 规则 → 任务 → 打分），与之并行的第二条路径是手工导入的「待处理任务」——按 `importType` 分「评价任务导入」(EVAL) 与「奖励分配」(REWARD)，两条路径物理隔离，仅在展示层合并列表。

**基础包名**: `com.bank.branch.platform.performance`
**Maven 坐标**: `com.bank.branch.platform:performance-engine-center`

## 依赖关系

- `common-web`/`common-trace`/`common-security`/`common-aop`/`common-db`
- `auth-permission-center`：`CurrentUserApi` / `BizScopeApi` / `OrgApi` / `RoleApi` / `UserApi`
- `system-governance-center`：`DictApi` / `FileApi` / `NotifyApi` / `AuditApi` / `JobApi`
- `workflow-center`：`WorkflowApi` / `WorkflowQueryApi` / `TodoQueryApi`（分配/目标调整审批走 BPMN）
- `portal-content-center`：`AddressBookApi`（指标结果导入的员工存在性校验）
- `customer-marketing-center`：`CustomerQueryApi`
- 不被任何业务模块依赖（`report-analytics-center` 只读引用其 `*QueryApi`，不计入依赖图的"被依赖"箭头）

## 架构规则与红线

- 跨模块调用一律走对方 `*Api`/`*QueryApi`，禁止直连 `mapper`/`entity`/`serviceImpl`。
- 新增数据库访问一律走 MyBatis-Plus（根 CLAUDE.md 红线）；`eval` 子域（如 `EvalRewardItemMapper`/`EvalUserTagMapper`）是本模块的示范实现，仿写时以它为准而非主干旧 Mapper。
- Controller 方法必标 `@BizAuth`，但 **`bizType` 分裂为两套体系**（详见下节「关键实现要点」第 1 条），改动前务必确认自己在哪个包。
- 严格 TDD 红-绿-重构闭环，每步独立 commit。
- 架构守护测试（均在 `src/test/.../arch/` 包）：`BizAuthConsistencyArchTest`、`BizAuthRequiredArchTest`（2026-07-19 新增，见下）、`NoEntityInControllerArchTest` / `NoEntityInControllerLocalsArchTest`、`NoOldDailyKpiCalcArchTest`、`NoUoeInFacadeTestsArchTest`、`NoV11UOEArchTest`。改动前先跑一遍，比通读历史变更日志更快确认是否会撞红线。

## 关键实现要点与踩坑

1. **BizType 分裂两套体系**：`controller/`（绩效计算主干）只能用 `BizType.PERF_CONFIG` 或 `BizType.KPI_CALC`；`eval/controller/` 统一用 `BizType.EVAL`。`BizAuthConsistencyArchTest` 的 `@AnalyzeClasses` 仅扫描 `performance.controller` 包，**不覆盖 `performance.eval.controller`**（这是刻意保留的设计——它断言的是"bizType 必须落在单档约束内"，两套体系不能合并扫描，否则 eval 合法使用 `BizType.EVAL` 会被误判违规）——不要误以为这个架构测试也在管 eval，新增 eval 端点时不要机械套用 `PERF_CONFIG`。**2026-07-19 补充**：`BizAuthConsistencyArchTest` 本身只校验"若声明 `@BizAuth` 则 bizType 必须单档"，从未要求"必须声明"——`MetricBatchCalcController.trigger` 长期缺失 `@BizAuth` 却未被拦截即因于此。新增的 `BizAuthRequiredArchTest` 补上"所有 Controller 的 HTTP 处理方法必须声明 `@BizAuth`"这条规则，且同时扫描 `performance.controller` + `performance.eval.controller` 两个包；该测试内置一份显式豁免清单（`EXEMPT_METHODS`），当前仅 `DataTaskController.reportStatus`（外部数据上报回调，无前端会话意义上的数据范围）一条，新增豁免必须写明理由。
2. **`PerfQuartzConfig` 类已不存在**：磁盘上已确认没有这个类（历史文档/部分旧 Javadoc 注释仍提及，均属遗留痕迹）。当前调度统一走 `sys_job_conf` 声明式动态注册：governance `JobApi.registerJob(RegisterJobCmd)` / `unregisterJob(jobKey)`，底层由 `JobService.syncJobsOnStartup` 在 `ApplicationReadyEvent` 后扫描 `sys_job_conf` 表同步到 Quartz；所有 Job 包装类直接 `implements Job` 且不加 `@Component`，靠反射 + `AutowiringSpringBeanJobFactory` 注入。
3. **`MetricSchedulerService.register()` 已被运维要求关停为空实现**：方法体仅打一行 debug 日志直接返回，注释明确写明"按运维要求关停自动写入 SYS_JOB_CONF / QRTZ_*"。这是当前生产状态而非可抄的范例代码——`syncOnStartup`/`MetricSchedulerHealthCheck` 仍会调用它，但实际不再写入任何调度配置。
4. **KPI 重算是 best-effort 事件驱动 + 10 分钟补偿的降级语义**：`MetricCalcCompletedEvent` → `KpiCascadeListener`（`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + `@Async`）正常情况下实时触发重算；`calcMetric` 写状态成功但事件 publish 前 JVM 崩溃则事件永久丢失，此时靠 `MetricSchedulerHealthCheck` 每 10 分钟一次的扫描间接补偿——不保证强一致，但保证最终会补上。
5. **分布式锁固定在 Facade 层申请/释放**：`SysControlFacade`（切版/回滚）、`MetricLifecycleFacade`（指标执行）均在 Facade 层调 `LockManager.tryLock`/`unlock`，Service 层保持纯 `@Transactional`，避免 Service 内部方法互调导致重复加锁/死锁。**例外**：`DataTaskService.report`（外部数据上报幂等）锁直接持在 Service 层，因其对应 Facade（`DataTaskApiImpl`）是单方法薄委托、无其他 Service 编排需求，不违反约定初衷。
6. **eval「单一角色化」是当前终态**：`EVAL_USER_TAG` 每人至多一个标签（`entity/EvalUserTag` 注释明确"无 role_type"，Mapper 方法为单数 `selectTagIdByUserId`）；「被评价/评价」方向不存在于人员侧，完全由规则承载——标签出现在 `EVAL_RULE.beEvalTagId` 即被评价，出现在 `EVAL_RULE_GROUP.evalTagId` 即评价人。历经三次迭代（标签加类型 → 去类型 → 单一角色化）的原因是产品侧发现"被评价/评价"本质是标签的使用位置而非标签自身的属性，收窄到当前模型后不再需要人员侧的角色字段。
7. **`PERF_RUN_TASK` 无 `uk_task_key` 唯一键（勘误）**：`docs/schema/ddl-performance.sql` 中该表仅 `PRIMARY KEY(id)` + 普通索引（`idx_task_type`/`idx_status`/`idx_started_by`/`idx_created_time`/`idx_type_trigger`），`task_key` **非唯一**，每个指标会累积多行记录。注意 `DataTaskService.java` 内部分注释仍按"加了 uk_task_key 唯一键"的口径描述 `DuplicateKeyException` 兜底分支，这段注释与当前实际 DDL 不符，凡按"task_key 唯一"设计的逻辑均需以本条勘误为准。
8. **REWARD 分配值可 0 不可负**：`EvalRewardService` 校验分配值 `assignValue == null || assignValue.compareTo(BigDecimal.ZERO) < 0` 时抛 `PerfErrorCode.EVAL_REWARD_ASSIGN_NEGATIVE`（`PERF-40072`），即允许 0、仅禁止负数和空值；一组分配值之和必须严格等于该组分配合计（`EVAL_REWARD_SUM_MISMATCH`/`PERF-40073`）。
9. **导出/上传统一走 OBS**：`KpiExportStrategy` 源码注释明确"上传 OBS（统一走 FileApi）"，`MetricExportStrategy`/`AllocExportStrategy`/`DetailExportStrategy` 同样经 governance `FileApi` 落地到华为云 OBS（`ObsStorageClient`），**不是 MinIO**。模块内仍有历史遗留的 Javadoc/字段注释（如 `PerfExportTask.fileKey`、`ExportTaskRespDTO` 字段说明）写着"MinIO"，与 `system-governance-center` 同类遗留注释性质一致，均不代表实际实现，以代码行为为准。

## 已知技术债/例外

- 上条第 9 点提到的"MinIO"遗留措辞分散在多处 Javadoc/字段注释里，尚未统一清理，阅读时需甄别。
- eval/REWARD 子域表结构尚未回写 `docs/modules/performance-engine-center/05-表结构DDL.md`，以 `docs/schema/ddl-eval.sql` + `docs/superpowers/sql/*eval*.sql` 系列迁移脚本为准；`ddl-eval.sql` 基线本身也滞后于最新迁移（例如仍显示 `EVAL_USER_TAG.ROLE_TYPE` 列），实际结构以当前 entity 源码 + 已在目标库执行的迁移脚本为准，不要单看基线文件下结论。
- 本项目已废弃 Flyway，schema 变更一律手工执行 SQL（见根 CLAUDE.md「Flyway 禁令」），因此本模块的 DDL 演进散落在多份增量脚本里，无法只看一份文件得到当前全貌。
- **eval 控制器直接返回 entity（2026-07-19 冻结的存量技术债）**：`NoEntityInControllerArchTest`/`NoEntityInControllerLocalsArchTest` 原先的包名判定硬编码前缀，无法识别同级包 `performance.eval.entity`（不是 `performance.entity` 的子包），导致 eval 侧多个 Controller 直接返回/依赖 `EvalTag`/`EvalRule`/`EvalTask`/`EvalTaskTarget`/`EvalAssignBatch`/`EvalUserTag` 等实体的现状长期未被架构测试捕获。本次已修复两个测试的包名识别逻辑，但**未做大范围 DTO 化重构**，而是把已发现的存量违规显式冻结在两处白名单里（`NoEntityInControllerArchTest.FROZEN_VIOLATIONS` 按方法签名登记 14 个方法；`NoEntityInControllerLocalsArchTest.FROZEN_EXEMPT_CONTROLLERS` 按类登记 7 个 Controller：`EvalAssignBatchController`/`EvalRewardAdminController`/`EvalRuleController`/`EvalScoreController`/`EvalTagController`/`EvalTaskController`/`EvalUserTagController`）——**清单内方法/类允许沿用旧写法，新增违规一律禁止**。后续如有余力应逐个改造为返回 DTO 并从两份清单中移除，改造前先跑一遍这两个测试确认没有遗漏。
- **`DataTaskController.reportStatus` 不标 `@BizAuth` 与"API Token"文档不符**：源码 Javadoc（03 §G.1）称该端点走外部 API Token 鉴权、不走前端登录态；经核实仓库内**并未**实现任何 `X-Api-Token`/`sys_api_token` 校验逻辑（纯文档层面的将来时设计），该端点实际仍受 `auth-permission-center` 的 `AuthenticationFilter`（Session 校验）与 `AuthorizationInterceptor`（`PT_RESOURCE` + RBAC）保护，只是跳过了 `@BizAuth` 对应的细粒度 BizType/DataScope 校验。`BizAuthRequiredArchTest` 已将其列入显式豁免清单（`EXEMPT_METHODS`）。是否要补齐真实的 API Token 机制或者干脆也给它补上 `@BizAuth`，需产品/架构侧另行决策。
- **`/api/perf/metric-calc/trigger` 匿名白名单已摘除（2026-07-19 收口）**：该 URL 曾同时登记在 `auth-permission-center` 的 `AuthenticationFilter.WHITELIST` 与 `WebMvcAuthConfig` 的 `excludePathPatterns`（2026-05-27 提交 `326bba98`，调试期便捷通道），期间任何人可匿名触发批量计算。经全仓排查确认无前端/脚本/网关裸调方后，两处白名单已删除，恢复 Session + RBAC + `@BizAuth(PERF_CONFIG, EXECUTE)` 全链路；`P_PERF_CALC_TRIG` 资源与角色绑定在生产基线齐备，授权用户不受影响。回归防护：`bootstrap` 的 `PerfTriggerAuthWhitelistRemovalIT`（redengine-smoke profile 真实鉴权链）固定断言匿名 401/登录后可达。**运维提醒**：此前依赖裸 curl 调该端点的操作方式失效，需改为带会话调用。

## 清单与契约指引

- Controller/端点/DTO/错误码/表清单以源码目录（`src/main/java/com/bank/branch/platform/performance/`）为准，不在本文件维护数量或逐条列表。
- 端点契约细节：`docs/modules/performance-engine-center/03-接口设计与报文.md`、`04-对外API契约.md`。
- 示例代码统一登记在 `docs/code-examples.md`（含 OBS 上传、MyBatis-Plus 标准 CRUD、自定义 SQL XML 等范例），本文件不复制代码片段。
- 共通开发规范：`docs/common-dev-guide.md`。

## 测试指引

- `*Test.java` 走 surefire（`mvn test`），`*IT.java` 走 failsafe（`mvn verify`，`mvn test` 不跑）；跨模块改动后按根 CLAUDE.md「Stale jar 处理」先 `mvn clean install -DskipTests` 再跑 IT。
- 单线程 Mapper IT 继承 `PerformanceMapperTestBase`（含 `@Transactional`）；并发场景改继承 `PerformanceConcurrentTestBase`（**不含** `@Transactional`）+ `TestDbCleaner` + 独立前缀隔离（`@Transactional + @Rollback` 与多线程测试的线程本地事务绑定不兼容）。
- `support/PerfTestConfig` 是公共 mock bean 装配点，新引入的外部 `*Api` 依赖必须先在此加 mock，否则相关测试的 Spring 上下文加载会直接失败。
