# system-governance-center/AGENTS.md

本文件补充根 [AGENTS.md](../AGENTS.md)，适用于系统治理中心。

## 模块边界

本模块提供字典、系统配置、工作日历、审计、通知、文件和定时任务治理能力，基础包为 `com.bank.branch.platform.governance`。

- 依赖 common 和 `auth-permission-center`；其他模块只能通过本模块 `api/` 中的 `*Api` 与 DTO 使用治理能力。
- 禁止其他模块访问本模块 mapper、entity、内部 service 或私有表。
- 方法、端点与表结构以源码和 `docs/modules/system-governance-center/` 下的契约文档为准；不在本文件维护清单。

## 平台唯一集成点

- 本模块是平台 Quartz 集成点。业务模块提交可调度的业务方法和 Job 包装类，通过 `JobApi` 声明式登记；不得自行创建平行 Scheduler、模块级 Trigger 配置或 Redis/ShedLock 防重方案。
- `AuditLogHandler` 是 common-aop 的 SPI，本模块 `GovAuditLogHandler` 提供平台持久化实现。其他业务模块不要再用 `@Primary` 覆盖它；确需替换属于平台级架构变更。
- 文件存储实现是 `ObsStorageClient` + 华为云 OBS。历史注释、错误码名或 DTO 中残留的 “MinIO” 只是旧命名，不能作为运行实现依据。

## 缓存与 OBS

- 字典、配置和日历缓存使用本地 Caffeine。兼容签名中的 TTL 参数不代表支持逐条目 TTL；修改缓存语义前评估多节点一致性和失效方式。
- OBS 配置从运行配置注入，禁止在代码、测试、日志和文档中写入凭据。`ObsStorageClient` 初始化不应把外网可达作为 Spring 上下文启动前提；禁用 OBS 时所有误调用必须明确 Fail Close。
- 文件元数据与 OBS 对象的一致性由 `FileService` 维护。删除、去重和事务补偿要考虑对象可能被多个业务记录引用，不能只凭历史 “MinIO” 注释推断行为。

## Quartz 动态调度

- 启动完成后，`JobService` 从 `sys_job_conf` 同步 JobDetail/Trigger；Scheduler 不可用或明确禁用时应安全跳过，不能阻断整个应用上下文。
- 注册和注销通过 `JobApi` 原子维护 `sys_job_conf` 与 Scheduler 运行态。新增作业不得要求重启，也不得恢复各模块自建 Quartz 配置的旧模式。
- 作业执行状态由全局 `JobExecutionLogger` 统一写入运行日志；业务 Job 不要重复维护同一套开始/成功/失败日志。
- Quartz 集群依赖数据库 JobStore 和 `QRTZ_LOCKS` 实现单次执行；不要叠加 `PT_LOCK`、Redis 锁或 ShedLock。Job 实例由 Quartz 反射创建时，依赖注入统一交给 `AutowiringSpringBeanJobFactory`。
- 日常巡检、故障处理和补偿遵循 [运维 Runbook](../docs/modules/system-governance-center/09-运维Runbook.md)。运行库未初始化或 schema 不匹配时停止并走数据库审批流程，不得让应用自动建表。

## Session 孤儿清理

`SpringSessionCleanupQuartzJob` 与 `SpringSessionCleanupService` 清理无外键级联环境下的孤儿 `SPRING_SESSION_ATTRIBUTES`。该任务使用 `sys_job_conf` 与统一调度/日志机制，不创建独立业务实体，也不要给 Job 类加会造成重复实例的组件注册。

## 审计

- `@AuditLog` 触发 common-aop 后，由 `GovAuditLogHandler` 转换并持久化治理审计记录。
- 审计写入失败是否影响主交易必须遵循明确契约；高危操作要求失败也审计时，不能只依赖成功返回后触发的通用切面。
- 审计入参、出参和异常信息必须脱敏，禁止落对象存储凭据、Session、令牌或完整敏感业务数据。

## 验证

- 单元和集成测试使用 `src/test/resources` 中的隔离配置；执行前核对 profile 和数据源，OBS 必须 mock 或显式禁用。
- Quartz 变更要覆盖启动同步、动态注册/注销、集群/并发语义、监听日志以及 Scheduler 缺失时的降级。
- 文件变更要覆盖格式/大小校验、去重、上传下载删除失败和 OBS 禁用隔离；跨模块测试前按根文件刷新本地 SNAPSHOT。
