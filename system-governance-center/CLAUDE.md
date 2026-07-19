# system-governance-center/ CLAUDE.md

本文件为 `system-governance-center` 模块提供上下文说明，是本模块开发指导的唯一权威来源。

## 模块概述

**system-governance-center** 是系统治理中心，提供字典管理、系统配置、工作日历、审计日志、通知、文件管理（华为云 OBS）、定时任务（Quartz 集群调度）等通用治理能力。

**基础包名**: `com.bank.branch.platform.governance`

**Maven 坐标**: `com.bank.branch.platform:system-governance-center`

**对外契约**: 一组 `*Api` 接口（`DictApi`/`ConfigApi`/`CalendarApi`/`AuditApi`/`NotifyApi`/`FileApi`/`JobApi`），供所有业务模块依赖。具体方法签名以源码 `api/` 目录和 `docs/modules/system-governance-center/04-对外API契约.md` 为准。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`
- **被依赖**: 所有需要字典、配置、日历、审计、通知、文件、任务管理的模块
- **被 workflow-center 依赖**: `CalendarApi`（SLA 计算）+ `NotifyApi`（任务通知）

## 架构规则与红线

- 模块间只能通过 `api/` 下的 `*Api` 接口交互，禁止其他模块直连本模块 `mapper`/`entity`/`serviceImpl`。
- 治理中心是平台**唯一**的 Quartz 集成点：业务模块只持有"裸业务方法" + `QuartzJobBean` 包装类，不得自行引入 Quartz 调度或 ShedLock/Redis 类防重机制。
- `AuditLogHandler`（`common-aop` 定义的 SPI）由本模块的 `handler/GovAuditLogHandler` 提供 `@Primary` 实现，其他模块不应再实现该 SPI 覆盖它。

## 关键实现要点与踩坑

### 缓存与存储后端已去 Redis / 去 MinIO

- `DictApi`/`ConfigApi`/`CalendarApi` 的缓存已从 Redis 切换为 `config/MemoryCacheService`（Caffeine），`put` 方法保留 `ttl` 入参以兼容原 `RedisTemplate` 调用签名，但实际不区分 per-entry TTL；`GovCacheConfig`（`RedisTemplate`）已不存在。
- 文件存储已从 MinIO 切换为 `storage/ObsStorageClient` 直接管理华为云 OBS 连接，`MinioConfig`（`MinioClient` bean）已不存在；接口内部若干 Javadoc 注释仍写着"MinIO"（历史遗留未更新，不代表实现），以代码为准。
- `ObsStorageClient` 是**懒连接**：`@PostConstruct` 只构造客户端对象，不发起网络请求，首次 put/get 才真正连接 OBS。

### sys_job_conf 动态注册机制

- `JobService.syncJobsOnStartup` 在 `ApplicationReadyEvent` 后扫描 `sys_job_conf` 表，按 `jobKey` 反射 `Class.forName` 批量同步 `JobDetail`/`Trigger` 到 Quartz（`overwrite-existing-jobs=true`，配置变更可覆盖 QRTZ_* 数据）；`Scheduler` bean 不可用（测试或禁用 Quartz 场景）时检测并跳过，不阻塞 ApplicationContext 加载。
- 业务模块通过 `JobApi.registerJob(RegisterJobCmd cmd)`/`unregisterJob(jobKey)` **声明式**注册/注销调度任务（原子 `sys_job_conf` upsert + 动态注入/移除 Scheduler），无需重启即可生效。**各业务模块自行声明 `JobDetail`/`Trigger` Spring Bean 的旧模式（per-module `QuartzConfig`）已废弃**，新增定时任务一律走 `JobApi.registerJob`，不要再手写模块级 Quartz 配置类。
- 写日志由全局 `JobExecutionLogger`（Quartz `JobListener`，在 `QuartzConfig` 中注册）统一处理：`jobToBeExecuted` 写 RUNNING、`jobWasExecuted` 写 SUCCESS/FAILED 到 `sys_job_run_log`，业务模块不再需要显式调用 `startJobRun`/`completeJobRun`/`failJobRun`（`JobService` 同名方法仍 public，仅供 `JobController` 内部使用）。

### Quartz 集群防重

- `org.quartz.jobStore.isClustered=true`：多节点部署时通过 `QRTZ_LOCKS` 行锁实现单一执行（替代 ShedLock + Redis）。
- `JobStore` 由 `SchedulerFactoryBean.afterPropertiesSet` 通过 `putIfAbsent` 注入 `LocalDataSourceJobStore`（`JobStoreCMT` 子类）；DDL 由 `docs/schema/ddl-quartz.sql` 手动初始化（`spring.quartz.jdbc.initialize-schema=never`）。
- `AutowiringSpringBeanJobFactory` 继承 `SpringBeanJobFactory`，Quartz 反射创建 Job 实例后补齐 `@Autowired` 字段注入。
- **日常巡检、故障排查、手动补偿等运维操作权威指南**：[`docs/modules/system-governance-center/09-运维Runbook.md`](../docs/modules/system-governance-center/09-运维Runbook.md)，本文件只保留架构决策与踩坑。

### SPRING_SESSION 孤儿数据清理

`job/SpringSessionCleanupQuartzJob`（Quartz 反射创建，不加 `@Component`）+ `service/SpringSessionCleanupService` 定时清理孤儿 `SPRING_SESSION_ATTRIBUTES` 行：GoldenDB（分布式库）去外键后 `SPRING_SESSION` 与 `SPRING_SESSION_ATTRIBUTES` 之间无级联删除，session 过期只删主表不删属性表会产生孤儿数据，需独立 Job 兜底清理；该 Job 不对应独立业务实体，走 `sys_job_conf` 登记调度，与业务 Job 用同一套注册/执行/日志机制。

### 审计日志集成

`GovAuditLogHandler` 是 `common-aop` 中 `AuditLogHandler` SPI 接口的 `@Primary` 实现：任何模块的代码标注 `@AuditLog` 时，AOP 框架触发 `AuditLogEvent`，`GovAuditLogHandler` 将其转换为 `AuditLogCmd` 并写入 `audit_log` 表，捕获所有异常确保审计日志写入失败不会阻塞主业务事务。

### JobApi 演进结论

`JobApi` 已从早期"业务模块显式调用 startJobRun/completeJobRun/failJobRun 写日志"演进为"Quartz 集群统一调度 + 全局 JobListener 统一写日志 + 声明式 registerJob/unregisterJob"，当前只对外暴露 `getJobConf`（只读查询）/`registerJob`/`unregisterJob` 三个方法；更细的版本演进历史见 `git log`，不在本文件维护。

## 清单与契约指引

- 控制器/Service/Mapper/实体的完整清单以 `src/main/java/com/bank/branch/platform/governance/` 源码目录为准，不在本文件维护。
- 端点契约、请求/响应报文以 `docs/modules/system-governance-center/03-接口设计与报文.md`、`04-对外API契约.md` 为准，每次接口变更同步更新这两份文档。
- 表结构见 `docs/modules/system-governance-center/05-表结构DDL.md`。
- 示例代码统一登记在 `docs/code-examples.md`，本文件不复制代码片段。

## 测试指引

- 单元测试使用 H2 内存库，配置见 `src/test/resources/schema.sql`/`data.sql`。
- Quartz 相关行为通过真实机制验证（`config/QuartzConfigIntegrationIT`、`service/JobApiRegisterQuartzIT`、`service/JobServiceQuartzIntegrationIT` 等 `*IT.java`），覆盖集群防重、动态注册/注销、启动同步等场景。
- `*Test.java`/`*Tests.java` → surefire（`mvn test` 触发）；`*IT.java` → failsafe（`mvn verify` 触发）。
- 跨模块 `@SpringBootTest`（bootstrap 层）依赖本模块最新类时，按根 CLAUDE.md 的 stale jar 处理流程先 `mvn clean install -DskipTests`。
