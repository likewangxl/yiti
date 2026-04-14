# system-governance-center/AGENTS.md

本文件为 `system-governance-center` 模块提供上下文说明。

## 模块概述

**system-governance-center** 是系统治理中心，提供字典管理、系统配置、工作日历、审计日志、通知、文件管理、定时任务等通用治理能力。

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
├── api/dto/          # 请求/响应 DTO (15 个类)
├── controller/       # REST 控制器 (8 个)
├── config/           # Spring 配置 (GovCacheConfig, MinioConfig)
├── entity/           # 数据库实体 (9 类)
├── facade/           # API 实现 (7 个 @Service)
├── handler/          # GovAuditLogHandler (实现 common-aop 的 AuditLogHandler SPI)
├── mapper/           # MyBatis Mapper (9 个接口)
└── service/          # 业务逻辑 (7 个 Service)
```

## 7 个对外 API 接口

### DictApi (Redis 缓存 TTL 10 分钟)

| 方法 | 用途 |
|------|------|
| `getDictItems(dictType)` | 获取指定类型的启用字典项 (已排序) |
| `getDictItem(dictType, dictCode)` | 获取单个字典项 |
| `getDictLabel(type, code)` | 获取显示标签, 找不到时返回 code |
| `batchGetDictItems(dictTypes)` | 批量获取多种类型字典项 |
| `isValidDictValue(type, value)` | 校验值是否存在且为 ACTIVE 状态 |

### ConfigApi (Redis 缓存 TTL 10 分钟)

| 方法 | 用途 |
|------|------|
| `getConfigValue(key)` | 获取配置值 (Optional) |
| `getConfigValue(key, defaultValue)` | 获取配置值, 带默认值 |
| `getConfigValue(key, Class<T>)` | 类型化获取 (Integer/Long/Boolean/String) |

### CalendarApi (Redis 缓存 TTL 24 小时)

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
| `upload(file, uploadedBy)` | 上传文件到 MinIO, 后缀白名单校验, MD5 去重 |
| `getDownloadUrl(fileId)` | 获取 MinIO 预签名下载 URL (1 小时有效) |
| `bindFile(bizType, bizId, fileObjectId, fileRole)` | 关联文件到业务对象 (幂等) |
| `listBizFiles(bizType, bizId)` | 获取业务对象关联的文件列表 |
| `deleteFile(fileId)` | 删除文件及所有关联 |

### JobApi

| 方法 | 用途 |
|------|------|
| `getJobConf(jobKey)` | 获取定时任务配置 |
| `startJobRun(jobId, trigger, empId)` | 记录任务执行开始, 返回 runLogId |
| `completeJobRun(runLogId)` | 标记执行为 SUCCESS |
| `failJobRun(runLogId, errorMsg)` | 标记执行为 FAILED |

## REST 端点

| Controller | 路径 | 鉴权 | 说明 |
|------------|------|------|------|
| DictController | `/api/sys/dicts/` (公开), `/api/admin/sys/dicts` (CRUD) | `@BizAuth(SYS_CONFIG, CONFIG/READ)` | 字典管理 |
| ConfigController | `/api/admin/sys/configs` (分页, 更新) | `@BizAuth(SYS_CONFIG, CONFIG/READ)` | 系统 KV 配置 |
| CalendarController | `/api/admin/sys/calendar` (获取年份, 切换, 初始化) | `@BizAuth(SYS_CONFIG, CONFIG/READ)` | 工作日历管理 |
| AuditLogController | `/api/admin/audit-logs` | `@BizAuth(SYS_CONFIG, READ)` | 审计日志查询 (禁止删除/编辑) |
| NotificationController | `/api/notifications` (列表, 未读数, 标记已读) | 无 `@BizAuth` (用户端) | 用户通知收件箱 |
| FileController | `/api/files/upload`, `/api/files/{fileId}/download-url` | 部分鉴权 | 文件上传/下载/绑定 |
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
| `file_object` | FileObject | MinIO 文件元数据 (fileName, fileSize, fileType, md5Hash) |
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
| `GovCacheConfig` | 定义 `RedisTemplate<String, Object>` (String key 序列化, JSON value 序列化), `@ConditionalOnMissingBean` 避免与其他模块冲突 |
| `MinioConfig` | 创建 `MinioClient` bean (从 `minio.endpoint`, `minio.access-key`, `minio.secret-key`, `minio.bucket` 读取) |
