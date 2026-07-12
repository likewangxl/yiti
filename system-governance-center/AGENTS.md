<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-03 | Updated: 2026-07-12 -->
# system-governance-center/AGENTS.md

本文件为 `system-governance-center` 模块提供当前实现态上下文。若与 `CLAUDE.md` 或 `docs/modules/system-governance-center/*` 冲突，以 `src/main/java`、`src/test/java` 为准。Quartz 集群调度运维权威指南见 `docs/modules/system-governance-center/09-运维Runbook.md`。

## 模块概述

**system-governance-center** 是系统治理中心，提供字典管理、系统配置、工作日历、审计日志、通知、文件管理（华为云 OBS）、定时任务（Quartz 集群调度）等通用治理能力。

**基础包名**: `com.bank.branch.platform.governance`

**Maven 坐标**: `com.bank.branch.platform:system-governance-center`

**对外契约**: 7 个 `*Api` 接口，供所有业务模块依赖。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`
- **被依赖**: 所有需要字典、配置、日历、审计、通知、文件、任务管理的模块
- **被 workflow-center 依赖**: CalendarApi (SLA 计算) + NotifyApi (任务通知)

## 包结构

```text
src/main/java/com/bank/branch/platform/governance/
├── api/              # 对外 API 接口 (7 个接口)
│   ├── DictApi.java / ConfigApi.java / CalendarApi.java / AuditApi.java
│   ├── NotifyApi.java / FileApi.java / JobApi.java
├── api/dto/          # 请求/响应 DTO (27 个类，含 V1.7 RegisterJobCmd)
├── controller/       # REST 控制器 (11 个，管理端/公开端拆分)
├── config/           # AutowiringSpringBeanJobFactory / MemoryCacheService / QuartzConfig
├── entity/           # 数据库实体 (9 类)
├── facade/           # API 实现 (7 个 @Service)
├── handler/          # GovAuditLogHandler (实现 common-aop 的 AuditLogHandler SPI)
├── job/              # SpringSessionCleanupQuartzJob（Quartz 反射创建，不加 @Component）
├── listener/         # JobExecutionLogger（全局 Quartz JobListener）
├── mapper/           # MyBatis-Plus Mapper (10 个接口)
├── service/          # 业务逻辑 (9 个 Service)
└── storage/          # ObsStorageClient + FileCategory（华为云 OBS 读写工具，替代原 MinIO）
```

## 7 个对外 API 接口

### DictApi (内存缓存 TTL 10 分钟)

| 方法 | 用途 |
|------|------|
| `getDictItems(dictType)` | 获取指定类型的启用字典项 (已排序) |
| `getDictItem(dictType, dictCode)` | 获取单个字典项 |
| `getDictLabel(type, code)` | 获取显示标签, 找不到时返回 code |
| `batchGetDictItems(dictTypes)` | 批量获取多种类型字典项 |
| `isValidDictValue(type, value)` | 校验值是否存在且为 ACTIVE 状态 |

### ConfigApi (内存缓存 TTL 10 分钟)

| 方法 | 用途 |
|------|------|
| `getConfigValue(key)` | 获取配置值 (Optional) |
| `getConfigValue(key, defaultValue)` | 获取配置值, 带默认值 |
| `getConfigValue(key, Class<T>)` | 类型化获取 (Integer/Long/Boolean/String) |

### CalendarApi (内存缓存 TTL 24 小时)

| 方法 | 用途 |
|------|------|
| `isWorkingDay(date)` | 判断是否为工作日 |
| `countWorkingDays(from, to)` | 统计两个日期间的个工作日天数 |
| `getWorkingDays(year)` | 获取某一年的所有工作日数据 |
| `addWorkingDays(from, workingDays)` | 计算从某天起 N 个工作日后的日期 |

> **缓存后端变更**：`DictApi`/`ConfigApi`/`CalendarApi` 的缓存已从 Redis 切换为 `config/MemoryCacheService`（Caffeine，`maximumSize=10000`、全局 `expireAfterWrite=1小时`），`put` 方法保留 `ttl` 入参以兼容原 `RedisTemplate` 调用签名，但实际不区分 per-entry TTL。模块内已无 `GovCacheConfig`/`RedisTemplate` bean。

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
| `upload(file, uploadedBy)` | 上传文件 |
| `upload(file, uploadedBy, category)` | 上传文件到 OBS，带类型前缀 `category`（见 `FileCategory`） |
| `upload(bytes, filename, contentType, uploadedBy, category)` | 字节直传（导出/导入/公告等无 MultipartFile 场景） |
| `getFileContent(fileId)` | 读取文件字节内容（供业务模块自行流式下载，替代原 `getFilePath` 本地读盘） |
| `getDownloadUrl(fileId)` | 获取 OBS 预签名下载 URL |
| `bindFile(bizType, bizId, fileObjectId, fileRole)` | 关联文件到业务对象 (幂等) |
| `listBizFiles(bizType, bizId)` | 获取业务对象关联的文件列表 |
| `deleteFile(fileId)` | 删除文件及所有关联 |
| `getFileName(fileId)` / `getFileSizes(fileIds)` / `getFileNames(fileIds)` | 文件名/大小批量查询，避免列表展示时逐行 N+1 |

> **存储后端已切华为云 OBS**：接口内部若干 Javadoc 注释仍写着"MinIO"（历史遗留未更新），但实现已全部改走 `storage/ObsStorageClient`（懒连接、预签名 URL、字节读写）；`config/MinioConfig` 已不存在。`FileController.downloadFile` 的方法注释写"302重定向到MinIO预签名URL"，但当前实现是读取字节后直接流式写回响应（`Content-Disposition: attachment`），并非重定向，以代码为准。

### JobApi（V1.7 扩展为 3 方法）

| 方法 | 用途 |
|------|------|
| `getJobConf(jobKey)` | 查询定时任务配置（只读） |
| `registerJob(RegisterJobCmd cmd)` | 业务模块声明式注册（或覆盖）一个 Quartz 调度任务（原子 sys_job_conf upsert + Scheduler 注入） |
| `unregisterJob(jobKey)` | 注销调度任务（幂等：不存在静默返回） |

- `registerJob`：原子写 `sys_job_conf`（upsert）+ 动态注入 Quartz Scheduler，支持启动后动态改动（可覆盖现有配置）
- `unregisterJob`：移除 `sys_job_conf` 行 + 删除 Quartz JobDetail/Trigger，幂等处理（不存在不报错）
- 错误码：GOV-50010 (JOB_CRON_INVALID) / GOV-50011 (JOB_CLASS_NOT_FOUND) / GOV-50012 (JOB_REGISTER_FAILED)
- 写日志由全局 `JobExecutionLogger`（Quartz `JobListener`）在 `jobToBeExecuted`/`jobWasExecuted` 回调中统一处理，业务模块不再需要 `startJobRun/completeJobRun/failJobRun`；`JobService` 同名方法仍 public，仅供 `JobController` 内部使用

## REST 端点

| Controller | 路径 | 鉴权 | 说明 |
|------------|------|------|------|
| DictController | `GET /api/sys/dicts`, `GET /api/sys/dicts/{dictType}/items` | 公开只读 | 字典类型列表 + 字典项查询 |
| AdminDictController | `POST/PUT/DELETE /api/admin/sys/dicts`, `PUT /api/admin/sys/dicts/{id}/status` | `@BizAuth(SYS_CONFIG, CONFIG)` | 字典增删改 + 启停 |
| ConfigController | `/api/admin/sys/configs` (分页, 更新) | `@BizAuth(SYS_CONFIG, CONFIG)` | 系统 KV 配置 |
| CalendarController | `/api/admin/sys/calendar` (获取年份, 切换, 初始化, 导入) | `@BizAuth(SYS_CONFIG, CONFIG)` | 工作日历管理 |
| PublicCalendarController | `GET /api/sys/calendar?year=&month=` | 公开只读 | 按月查询日历，无鉴权 |
| AuditLogController | `/api/admin/audit-logs` | `@BizAuth(SYS_CONFIG, READ)` | 审计日志查询 (禁止删除/编辑) |
| NotificationController | `/api/notifications` (列表, 未读数, 标记已读) | 无 `@BizAuth` (用户端) | 用户通知收件箱 |
| FileController | `/api/files/upload`, `/api/files/{fileId}/download`, `GET /api/files`, `DELETE /api/files/{fileId}` | 部分鉴权 | 按业务关联的文件上传/下载/查询/删除 |
| AdminFileController | `GET /api/admin/sys/files` (分页) | `@BizAuth(SYS_CONFIG, READ)` | 管理后台全局文件列表（区别于 FileController 的按业务关联查询） |
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

> `job/SpringSessionCleanupQuartzJob` + `service/SpringSessionCleanupService` + `mapper/SpringSessionMaintenanceMapper` 定时清理孤儿 `SPRING_SESSION_ATTRIBUTES`（GoldenDB 去外键后无级联删除），不对应独立业务实体，走 `sys_job_conf` 登记调度。

## 审计日志集成

`GovAuditLogHandler` 是 `common-aop` 中 `AuditLogHandler` SPI 接口的 `@Primary` 实现:
- 当任何模块的代码标注 `@AuditLog` 时, AOP 框架触发 `AuditLogEvent`
- `GovAuditLogHandler` 将其转换为 `AuditLogCmd` 并写入 `audit_log` 表
- 捕获所有异常, 确保审计日志写入失败不会阻塞主业务事务

## 配置类

| 配置类 | 说明 |
|--------|------|
| `MemoryCacheService` | Caffeine 内存缓存服务，`get`/`put`/`evict` 供 Dict/Config/Calendar Service 使用，替代原 `GovCacheConfig`（RedisTemplate）；开发/联调环境用，`maximumSize=10000`、全局 `expireAfterWrite=1小时` |
| `AutowiringSpringBeanJobFactory` | 继承 `SpringBeanJobFactory`，Quartz 反射创建 Job 实例后调用 `applicationContext.getAutowireCapableBeanFactory().autowireBean(job)` 补齐 `@Autowired` 字段注入 |
| `QuartzConfig` | 注册 `AutowiringSpringBeanJobFactory` 为 JobFactory + `JobExecutionLogger` 为全局 `JobListener`（通过 `SchedulerFactoryBeanCustomizer`），`waitForJobsToCompleteOnShutdown=true`；`JobService.syncJobsOnStartup` 在 `ApplicationReadyEvent` 后扫描 `sys_job_conf` 同步 JobDetail / Trigger 到 QRTZ_*（`overwrite-existing-jobs=true`）|

> `MinioConfig`（MinIO `MinioClient` bean）已不存在；文件存储改由 `storage/ObsStorageClient` 直接管理华为云 OBS 连接（`obs.endPoint`/`obs.accessKey`/`obs.secretKey`/`obs.bucketName` 配置），懒连接：`@PostConstruct` 只构造客户端，首次 put/get 才真正发起网络请求。

## Quartz 集群调度（V1.6 引入）

治理中心是平台唯一的 Quartz 集成点，业务模块只需提供裸 `run()` 方法 + `QuartzJobBean` 包装类。

- **集群与防重**: `org.quartz.jobStore.isClustered=true`，`QRTZ_LOCKS` 行锁替代 ShedLock + Redis
- **DDL**: `docs/schema/ddl-quartz.sql`（11 张 QRTZ_* 表，`spring.quartz.jdbc.initialize-schema=never` 手动初始化）
- **JobExecutionLogger**: 全局 `JobListener`，`jobToBeExecuted` 写 RUNNING + `jobWasExecuted` 写 SUCCESS/FAILED 到 `sys_job_run_log`，异常隔离不影响调度
- **动态注册（V1.7）**: 业务模块可通过 `JobApi.registerJob`/`unregisterJob` 声明式注册/注销调度任务，无需重启即可生效（原子 upsert `sys_job_conf` + 动态注入/移除 Scheduler）
- **运维 Runbook**：日常巡检、故障排查、手动补偿等运维操作以 [`docs/modules/system-governance-center/09-运维Runbook.md`](../docs/modules/system-governance-center/09-运维Runbook.md) 为准，本文件只保留架构速览。
