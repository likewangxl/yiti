# system-governance-center/ CLAUDE.md

本文件为 `system-governance-center` 模块提供上下文说明。

## 模块概述

**system-governance-center** 是系统治理中心，提供字典管理、系统配置、工作日历、审计日志、通知、文件管理（华为云 OBS，已替代早期 MinIO）、定时任务（Quartz 集群调度）等通用治理能力。

**基础包名**: `com.bank.branch.platform.governance`

**Maven 坐标**: `com.bank.branch.platform:system-governance-center`

**对外契约**: 7 个 `*Api` 接口，供所有业务模块依赖。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`
- **被依赖**: 所有需要字典、配置、日历、审计、通知、文件、任务管理的模块
- **被 workflow-center 依赖**: CalendarApi (SLA 计算) + NotifyApi (任务通知)

## 包结构

```
src/main/java/com/bank/branch/platform/governance/
├── api/              # 对外 API 接口 (7 个接口)
│   ├── DictApi.java
│   ├── ConfigApi.java
│   ├── CalendarApi.java
│   ├── AuditApi.java
│   ├── NotifyApi.java
│   ├── FileApi.java
│   └── JobApi.java
├── api/dto/          # 请求/响应 DTO (27 个类，含 V1.7 RegisterJobCmd)
├── controller/       # REST 控制器 (11 个，管理端/公开端拆分)
├── config/           # Spring 配置 (AutowiringSpringBeanJobFactory, MemoryCacheService, QuartzConfig)
├── entity/           # 数据库实体 (9 类)
├── facade/           # API 实现 (7 个 @Service)
├── handler/          # GovAuditLogHandler (实现 common-aop 的 AuditLogHandler SPI)
├── job/              # SpringSessionCleanupQuartzJob（Quartz 反射创建，不加 @Component）
├── listener/         # JobExecutionLogger（全局 Quartz JobListener）
├── mapper/           # MyBatis-Plus Mapper (10 个接口)
├── service/          # 业务逻辑 (9 个 Service)
└── storage/          # ObsStorageClient + FileCategory（华为云 OBS 读写工具，替代原 MinIO）
```

> **缓存/存储后端变更**：`GovCacheConfig`（`RedisTemplate`）与 `MinioConfig`（`MinioClient`）已不存在。`DictApi`/`ConfigApi`/`CalendarApi` 的缓存改由 `config/MemoryCacheService`（Caffeine）承载；文件存储改由 `storage/ObsStorageClient` 直接管理华为云 OBS 连接。

## 7 个对外 API 接口

### DictApi (内存缓存 Caffeine TTL 10 分钟)

| 方法 | 用途 |
|------|------|
| `getDictItems(dictType)` | 获取指定类型的启用字典项 (已排序) |
| `getDictItem(dictType, dictCode)` | 获取单个字典项 |
| `getDictLabel(type, code)` | 获取显示标签, 找不到时返回 code |
| `batchGetDictItems(dictTypes)` | 批量获取多种类型字典项 |
| `isValidDictValue(type, value)` | 校验值是否存在且为 ACTIVE 状态 |

### ConfigApi (内存缓存 Caffeine TTL 10 分钟)

| 方法 | 用途 |
|------|------|
| `getConfigValue(key)` | 获取配置值 (Optional) |
| `getConfigValue(key, defaultValue)` | 获取配置值, 带默认值 |
| `getConfigValue(key, Class<T>)` | 类型化获取 (Integer/Long/Boolean/String) |

### CalendarApi (内存缓存 Caffeine TTL 24 小时)

| 方法 | 用途 |
|------|------|
| `isWorkingDay(date)` | 判断是否为工作日 |
| `countWorkingDays(from, to)` | 统计两个日期间的个工作日天数 |
| `getWorkingDays(year)` | 获取某一年的所有工作日数据 |
| `addWorkingDays(from, workingDays)` | 计算从某天起 N 个工作日后的日期 |

### AuditApi (不缓存, 始终读最新数据)

| 方法 | 用途 |
|------|------|
| `log(cmd)` | 写入审计日志 (REQUIRES_NEW 事务, 确保主事务回滚不影响审计记录) |
| `queryLogs(query, page)` | 分页查询审计日志 |

### NotifyApi

| 方法 | 用途 |
|------|------|
| `sendNotification(cmd)` | 发送通知 (可通过 @TransactionalEventListener 异步) |
| `batchSendNotifications(cmds)` | 批量发送通知 |
| `countUnread(empId)` | 未读通知数量 (用于仪表盘组件) |
| `queryNotifications(empId, isRead, page)` | 分页查询通知列表 |

### FileApi

| 方法 | 用途 |
|------|------|
| `upload(file, uploadedBy)` | 上传文件到华为云 OBS（已替代原 MinIO）, 后缀白名单校验, MD5 去重 |
| `getDownloadUrl(fileId)` | 获取 OBS 预签名下载 URL (1 小时有效) |
| `bindFile(bizType, bizId, fileObjectId, fileRole)` | 关联文件到业务对象 (幂等) |
| `listBizFiles(bizType, bizId)` | 获取业务对象关联的文件列表 |
| `deleteFile(fileId)` | 删除文件及所有关联 |

### JobApi (V1.7 扩展为 3 方法)

| 方法 | 用途 |
|------|------|
| `getJobConf(jobKey)` | 查询定时任务配置（只读） |
| `registerJob(RegisterJobCmd cmd)` | V1.7 业务模块声明式注册（或覆盖）一个 Quartz 调度任务（原子 sys_job_conf upsert + Scheduler 注入） |
| `unregisterJob(jobKey)` | V1.7 注销调度任务（幂等：不存在静默返回） |

**V1.7 扩展说明（2026-04-30）**：V1.6 只保留了 `getJobConf` 只读查询方法。V1.7 为支持"指标级调度"引入声明式注册机制，业务模块（如 performance-engine-center）可在 CRUD 时自动或显式注册/注销 Job。

- `registerJob`：原子写 `sys_job_conf`（upsert）+ 动态注入 Quartz Scheduler，支持启动后动态改动（可覆盖现有配置）
- `unregisterJob`：移除 `sys_job_conf` 行 + 删除 Quartz JobDetail/Trigger，幂等处理（不存在不报错）
- 错误码扩展：GOV-50010 (JOB_CRON_INVALID) / GOV-50011 (JOB_CLASS_NOT_FOUND) / GOV-50012 (JOB_REGISTER_FAILED)

**V1.6 精简背景（2026-04-25）**：V1.0-V1.5 曾持有 `startJobRun(jobId, trigger, empId)` /
`completeJobRun(runLogId)` / `failJobRun(runLogId, errorMsg)` 三个写日志方法，由各业务模块的
`@Scheduled` 任务在执行前后显式调用。V1.6 quartz 整合后：

- 调度统一收敛到 Quartz 集群（`isClustered=true` + JDBC JobStore），防重由 `QRTZ_LOCKS` 行锁接管（不再依赖 ShedLock）
- 写日志改由 `JobExecutionLogger`（全局 Quartz `JobListener`）在 `jobToBeExecuted` / `jobWasExecuted` 回调中统一处理
- `JobApi` 仅保留 `getJobConf` 一个只读查询方法，业务模块不再需要写日志方法（spec 决策 #5=B）
- `JobService.startJobRun/completeJobRun/failJobRun` 被 `JobController` + `JobServiceTest` 内部使用，public 可见性保持不变（spec §8.2 不强制收敛）

## REST 端点

| Controller | 路径 | 鉴权 | 说明 |
|------------|------|------|------|
| DictController | `GET /api/sys/dicts`, `GET /api/sys/dicts/{dictType}/items` | 公开只读 | 字典类型列表 + 字典项查询 |
| AdminDictController | `POST/PUT/DELETE /api/admin/sys/dicts`, `PUT /api/admin/sys/dicts/{id}/status` | `@BizAuth(SYS_CONFIG, CONFIG)` | 字典增删改 + 启停 |
| ConfigController | `/api/admin/sys/configs` (分页, 更新) | `@BizAuth(SYS_CONFIG, CONFIG/READ)` | 系统 KV 配置 |
| CalendarController | `/api/admin/sys/calendar` (获取年份, 切换, 初始化, 导入) | `@BizAuth(SYS_CONFIG, CONFIG/READ)` | 工作日历管理 |
| PublicCalendarController | `GET /api/sys/calendar?year=&month=` | 公开只读 | 按月查询日历, 无鉴权 |
| AuditLogController | `/api/admin/audit-logs` | `@BizAuth(SYS_CONFIG, READ)` | 审计日志查询 (禁止删除/编辑) |
| NotificationController | `/api/notifications` (列表, 未读数, 标记已读) | 无 `@BizAuth` (用户端) | 用户通知收件箱 |
| FileController | `/api/files/upload`, `/api/files/{fileId}/download`, `GET /api/files`, `DELETE /api/files/{fileId}` | 部分鉴权 | 按业务关联的文件上传/下载/查询/删除 |
| AdminFileController | `GET /api/admin/sys/files` (分页) | `@BizAuth(SYS_CONFIG, READ)` | 管理后台全局文件列表 |
| JobController | `/api/admin/sys/jobs` (列表, 日志, 触发, 暂停, 恢复) | `@BizAuth(SYS_CONFIG)` | 定时任务管理 |
| SqlProbeController | `/api/admin/sql-probe/execute`, `/api/admin/sql-probe/history` | `@BizAuth(SYS_CONFIG, EXECUTE_SQL/READ)` | 受限只读 SQL 查询 + 全量审计 |

## 数据库表 (9 张)

| 表 | 实体 | 说明 |
|----|------|------|
| `sys_dict` | SysDict | 字典项 (dict_type + dictCode 唯一) |
| `sys_config_kv` | SysConfigKv | 系统 KV 配置 (configKey 唯一, value 为 longtext) |
| `sys_calendar_day` | SysCalendarDay | 工作日历天 (day 为主键, is_workday: 1/0) |
| `audit_log` | AuditLog | 审计日志 (immutable, 无 updated_time 字段) |
| `user_notification` | UserNotification | 用户通知收件箱 (title, content TEXT, is_read) |
| `file_object` | FileObject | OBS 文件元数据 (fileName, fileSize, fileType, md5Hash) |
| `biz_file_rel` | BizFileRel | 业务-文件关联 (biz_type + biz_id + fileObjectId 唯一, M:N) |
| `sys_job_conf` | SysJobConf | 定时任务定义 (jobKey 唯一, cronExpr, status) |
| `sys_job_run_log` | SysJobRunLog | 任务执行记录 (triggerType: SCHEDULED/MANUAL, status: RUNNING/SUCCESS/FAILED) |

## 审计日志集成

`GovAuditLogHandler` 是 `common-aop` 中 `AuditLogHandler` SPI 接口的 `@Primary` 实现:
- 当任何模块的代码标注 `@AuditLog` 时, AOP 框架触发 `AuditLogEvent`
- `GovAuditLogHandler` 将其转换为 `AuditLogCmd` 并写入 `audit_log` 表
- 捕获所有异常, 确保审计日志写入失败不会阻塞主业务事务

## 配置类

| 配置类 | 说明 |
|--------|------|
| `MemoryCacheService` | Caffeine 内存缓存服务 (`get`/`put`/`evict`)，供 Dict/Config/Calendar Service 使用，替代原 `GovCacheConfig`（`RedisTemplate`）；`maximumSize=10000`、全局 `expireAfterWrite=1 小时`，`put` 方法保留 `ttl` 入参兼容原调用签名但不区分 per-entry TTL |
| `AutowiringSpringBeanJobFactory` | 继承 `SpringBeanJobFactory`，Quartz 反射创建 Job 实例后补齐 `@Autowired` 字段注入 |
| `QuartzConfig` | V1.6 引入：注册 `AutowiringSpringBeanJobFactory` 为 JobFactory + `JobExecutionLogger` 为全局 `JobListener`（通过 `SchedulerFactoryBeanCustomizer`）；`JobService.syncJobsOnStartup` 在 `ApplicationReadyEvent` 后扫描 `sys_job_conf` 表批量同步 JobDetail / Trigger 到 Quartz |

> `MinioConfig`（`MinioClient` bean）已不存在；文件存储改由 `storage/ObsStorageClient` 直接管理华为云 OBS 连接（懒连接：`@PostConstruct` 只构造客户端，首次 put/get 才真正发起网络请求）。

## Quartz 集群调度（V1.6 引入）

V1.6 quartz 整合后，治理中心成为平台唯一的 Quartz 集成点，业务模块仅需提供"裸业务方法"
和 Quartz 包装类（`QuartzJobBean` 子类）：

### 关键组件

| 组件 | 路径 | 说明 |
|---|---|---|
| `QuartzConfig` | `config/QuartzConfig.java` | 注册 `JobExecutionLogger` 为全局 JobListener |
| `JobExecutionLogger` | `listener/JobExecutionLogger.java` | 全局 Quartz `JobListener`：`jobToBeExecuted` 写 RUNNING 日志 + `jobWasExecuted` 写 SUCCESS/FAILED 日志，异常隔离不影响业务调度 |
| `JobService.syncJobsOnStartup` | `service/JobService.java` | 应用启动期扫描 `sys_job_conf` 表，按 `jobKey` 批量同步 `JobDetail` / `Trigger` 到 Quartz（`overwrite-existing-jobs=true`，配置变更可覆盖 QRTZ_* 数据） |

### 集群与防重

- `spring.quartz.properties.org.quartz.jobStore.isClustered=true`：多节点部署时通过 `QRTZ_LOCKS` 行锁实现单一执行（替代 ShedLock + Redis）
- `JobStore` 由 `SchedulerFactoryBean.afterPropertiesSet` 通过 `putIfAbsent` 注入 `LocalDataSourceJobStore`（JobStoreCMT 子类）
- DDL 由 `docs/schema/ddl-quartz.sql` 手动初始化（`spring.quartz.jdbc.initialize-schema=never`）

### 业务模块接入约定

业务模块只需：
1. 持有"裸业务方法"（如 `DailyKpiCalcJob.run()`），不带任何调度注解
2. 提供 `QuartzJobBean` 子类作为包装（如 `DailyKpiCalcQuartzJob`），在 `executeInternal` 中调用裸方法
3. 在自己模块的 Quartz 配置类中（如 `PerfQuartzConfig`）声明 `JobDetail` + `Trigger` bean
4. **不需要**再调用 `JobApi.startJobRun/completeJobRun/failJobRun`，写日志由 `JobExecutionLogger` 统一处理

> **运维 Runbook**: V1.9 已整合 V1.6-V1.8 调度运维知识到独立文档：[`docs/modules/system-governance-center/09-运维Runbook.md`](../docs/modules/system-governance-center/09-运维Runbook.md)。本节为模块架构说明（保留）。
