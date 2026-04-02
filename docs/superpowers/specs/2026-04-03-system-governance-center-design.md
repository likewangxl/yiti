# System-Governance-Center 设计规格

## 概述

**目标**：实现系统治理中心模块，为全平台提供 8 个治理域的统一支撑能力。

**架构**：Spring Boot 3.2.3 + MyBatis + Redis 缓存 + MinIO 文件存储，遵循 CLAUDE.md 规范的模块化单体架构。模块对外通过 `*Api` 接口暴露能力，供其他模块本地注入调用。

**技术栈**：JDK 17, Maven, MyBatis 3.0.3, Redis 6.x, MinIO 8.5.7, Knife4j 4.4.0

**公共开发规范**：遵循 `docs/common-dev-guide.md`，核心要点：
- 统一响应：`ResponseWrapper.ok(data)` / `ResponseWrapper.ok(data, pageInfo)` / `ResponseWrapper.error(code, msg)`
- 分页：`PageResult.of(records, pageNo, pageSize, total)` + `result.toResponse()`
- DTO 分层：入参 `*ReqDTO`，出参 `*RespDTO`/`*VO`，跨模块 `*DTO`
- 审计：`@AuditLog` 注解驱动，高危操作需 reason + snapshot
- 领域事件：命名 `<domain>.<aggregate>.<past-tense>.v1`，事务提交后发布

---

## 分阶段交付计划

| Phase | 域 | 对外 API | 表 | 核心能力 |
|-------|-----|---------|-----|---------|
| 1 | Dict + Config + Calendar | DictApi, ConfigApi, CalendarApi | sys_dict, sys_config_kv, sys_calendar_day | 最基础，被所有模块依赖 |
| 2 | Audit + Notify | AuditApi, NotifyApi | audit_log, user_notification | 被流程和业务模块依赖 |
| 3 | File + Job | FileApi, JobApi | file_object, biz_file_rel, sys_job_conf, sys_job_run_log | MinIO 集成、定时任务 |
| 4 | SQL Probe + Controllers + Config | 无新 API | 无新表 | SQL 探针、全部 Controller、过滤器配置 |

---

## 包结构

```
com.bank.branch.platform.governance/
├── api/              # 对外接口（唯一可跨模块依赖）
│   ├── DictApi.java
│   ├── ConfigApi.java
│   ├── CalendarApi.java
│   ├── AuditApi.java
│   ├── NotifyApi.java
│   ├── FileApi.java
│   ├── JobApi.java
│   ├── dto/          # 响应 DTO
│   └── cmd/          # 命令对象（AuditLogCmd, NotificationCmd 等）
├── controller/       # REST 控制器（8 个）
├── facade/           # Api 实现类
├── service/          # 业务逻辑
├── mapper/           # MyBatis Mapper（模块私有）
├── entity/           # 数据库实体（模块私有）
├── enums/            # GovErrorCode, NotifyType, JobStatus 等
└── config/           # Redis, MinIO, WebMvc 配置
```

---

## 依赖关系

**Maven 依赖**：
- `common`（全部 5 个子模块：common-web, common-security, common-db, common-aop, common-trace）
- `auth-permission-center`（CurrentUserApi, BizScopeApi, OrgApi）
- `spring-boot-starter-web`, `spring-boot-starter-data-redis`, `mybatis-spring-boot-starter`
- `knife4j-openapi3-jakarta-spring-boot-starter`
- `minio` 8.5.7（Phase 3）

**被依赖**：portal-content-center, customer-marketing-center, workflow-center, performance-engine-center, report-analytics-center, business-application-center

---

## Phase 1：Dict + Config + Calendar

### Dict（字典管理）

**Entity**: `SysDict` → 表 `sys_dict`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| dictType | String | DICT_TYPE | 字典类型 |
| dictCode | String | DICT_CODE | 字典编码 |
| dictLabel | String | DICT_LABEL | 显示标签 |
| dictValue | String | DICT_VALUE | 字典值 |
| sortOrder | Integer | SORT_ORDER | 排序号 |
| status | String | STATUS | ACTIVE/DISABLED |
| remark | String | REMARK | 备注 |
| createdTime | LocalDateTime | CREATED_TIME | 创建时间 |
| updatedTime | LocalDateTime | UPDATED_TIME | 更新时间 |

**唯一约束**：`(DICT_TYPE, DICT_CODE)` 联合唯一

**DictApi 接口**：
```java
public interface DictApi {
    List<DictItemDTO> getDictItems(String dictType);
    String getDictLabel(String dictType, String dictCode);
    boolean isValidDictValue(String dictType, String dictCode);
    Map<String, List<DictItemDTO>> batchGetDictItems(Set<String> dictTypes);
}
```

**DictService**：
- 查询方法：`getDictItems`, `getDictLabel`, `isValidDictValue`, `batchGetDictItems`
- 管理方法：`createDict(dto)`, `updateDict(id, dto)`, `deleteDict(id)`（逻辑删除→DISABLED）, `toggleStatus(id)`
- 分页查询：`listByPage(dictType, keyword, pageNo, pageSize)`

**缓存策略**：
- Key: `gov:dict:{dictType}`
- TTL: 10 分钟
- 模式: Cache-Aside（读取时缓存未命中→查 DB→写缓存；写操作后主动 evict）
- DictFacade 中查询不存在的 dictType 时返回空 List（不抛异常）

### Config（系统配置）

**Entity**: `SysConfigKv` → 表 `sys_config_kv`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| configKey | String | CONFIG_KEY | 配置键（唯一） |
| configValue | String | CONFIG_VALUE | 配置值（TEXT） |
| valueType | String | VALUE_TYPE | STRING/JSON/NUMBER/BOOL |
| status | String | STATUS | ACTIVE/DISABLED |
| remark | String | REMARK | 备注 |
| createdTime | LocalDateTime | CREATED_TIME | 创建时间 |
| updatedTime | LocalDateTime | UPDATED_TIME | 更新时间 |

**ConfigApi 接口**：
```java
public interface ConfigApi {
    String getConfigValue(String configKey);
    String getConfigValue(String configKey, String defaultValue);
    <T> T getConfigValue(String configKey, Class<T> targetType);
}
```

**ConfigService**：
- 查询：`getConfigValue(key)`, `getConfigValue(key, defaultValue)`, `getConfigValue(key, Class<T>)`
- 类型转换：根据 valueType 字段将 configValue 转为目标类型（NUMBER→Long/Double, BOOL→Boolean, JSON→Jackson 反序列化）
- 管理：`listConfigs()`, `updateConfig(key, value)`（含类型校验）

**缓存策略**：Key=`gov:config:{configKey}`，TTL=10min，写后 evict

### Calendar（工作日历）

**Entity**: `SysCalendarDay` → 表 `sys_calendar_day`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| day | LocalDate | DAY | 主键，日期 |
| isWorkday | Integer | IS_WORKDAY | 0 非工作日 / 1 工作日 |
| remark | String | REMARK | 备注 |
| createdAt | LocalDateTime | CREATED_AT | 创建时间 |

**CalendarApi 接口**：
```java
public interface CalendarApi {
    boolean isWorkingDay(LocalDate date);
    int countWorkingDays(LocalDate from, LocalDate to);
    List<CalendarDayDTO> getWorkingDays(int year);
    LocalDate addWorkingDays(LocalDate from, int days);
}
```

**CalendarService**：
- 查询：`isWorkingDay(date)`, `countWorkingDays(from, to)`, `getWorkingDays(year)`, `addWorkingDays(from, days)`
- 管理：`toggleWorkday(date)`（翻转工作日状态）, `initYear(year)`（幂等初始化，跳过已存在日期，周一到周五默认为工作日）, `batchImport(List<CalendarDayDTO>)`（仅覆盖未来日期）
- 约束：过去日期不可修改

**缓存策略**：Key=`gov:calendar:{year}`，TTL=24h，写后 evict 对应年份

---

## Phase 2：Audit + Notify

### Audit（审计日志）

**Entity**: `AuditLog` → 表 `audit_log`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| traceId | String | TRACE_ID | 链路追踪 ID（从 MDC 获取） |
| empId | String | EMP_ID | 操作人工号 |
| empName | String | EMP_NAME | 操作人姓名 |
| bizType | String | BIZ_TYPE | 业务类型 |
| bizAction | String | BIZ_ACTION | 业务操作 |
| resourceUrl | String | RESOURCE_URL | 请求 URL |
| requestMethod | String | REQUEST_METHOD | HTTP 方法 |
| requestParams | String | REQUEST_PARAMS | 请求参数（脱敏后） |
| responseStatus | Integer | RESPONSE_STATUS | 响应状态码 |
| ipAddress | String | IP_ADDRESS | 客户端 IP |
| userAgent | String | USER_AGENT | 客户端 UA |
| executionTime | Long | EXECUTION_TIME | 执行耗时(ms) |
| reason | String | REASON | 操作原因 |
| createdTime | LocalDateTime | CREATED_TIME | 记录时间 |

**AuditApi 接口**：
```java
public interface AuditApi {
    void log(AuditLogCmd cmd);
    PageResult<AuditLogDTO> queryLogs(AuditLogQueryReqDTO query, int pageNo, int pageSize);
}
```

**AuditLogService**：
- 写入：`log(AuditLogCmd)` — 使用 `@Transactional(propagation = REQUIRES_NEW)` 独立事务，确保业务回滚不丢审计
- 查询：`queryLogs(query, page)` — 支持 empId/bizType/bizAction/dateRange/keyword 过滤
- 敏感参数脱敏：调用 `SensitiveDataMasker`（common-security 已提供）

**与 common-aop 集成**：
- 实现 `AuditLogHandler` 接口，接收 `@AuditLog` 注解切面拦截的审计事件
- 注册为 Spring Bean，替换 common-aop 的 `NoopAuditLogHandler` 默认实现

### Notify（通知）

**Entity**: `UserNotification` → 表 `user_notification`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| empId | String | EMP_ID | 接收人工号 |
| title | String | TITLE | 通知标题 |
| content | String | CONTENT | 通知内容 |
| notifyType | String | NOTIFY_TYPE | SYSTEM/WORKFLOW/BUSINESS |
| bizType | String | BIZ_TYPE | 关联业务类型 |
| bizId | String | BIZ_ID | 关联业务 ID |
| linkUrl | String | LINK_URL | 跳转链接 |
| isRead | Integer | IS_READ | 0 未读 / 1 已读 |
| readTime | LocalDateTime | READ_TIME | 阅读时间 |
| createdTime | LocalDateTime | CREATED_TIME | 创建时间 |

**NotifyApi 接口**：
```java
public interface NotifyApi {
    void sendNotification(NotificationCmd cmd);
    void batchSendNotifications(List<NotificationCmd> cmds);
    int countUnread(String empId);
    PageResult<NotificationDTO> queryNotifications(String empId, Boolean isRead, int pageNo, int pageSize);
}
```

**NotificationService**：
- 发送：`sendNotification(cmd)` — 同步写入 DB
- 批量发送：`batchSendNotifications(cmds)`
- 查询：`queryNotifications(empId, isRead, page)` — 按 createdTime DESC
- 标记已读：`markAsRead(id)`, `markAllAsRead(empId)`
- 统计：`countUnread(empId)`

**事务安全**：Facade 层使用 `@TransactionalEventListener(phase=AFTER_COMMIT)` 防止业务回滚后产生幽灵通知

**枚举**：`NotifyType`(SYSTEM/WORKFLOW/BUSINESS)

---

## Phase 3：File + Job

### File（文件管理）

**Entity**:
- `FileObject` → 表 `file_object`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| fileName | String | FILE_NAME | 原始文件名 |
| fileSize | Long | FILE_SIZE | 文件大小(bytes) |
| fileType | String | FILE_TYPE | MIME 类型 |
| storagePath | String | STORAGE_PATH | MinIO 存储路径 |
| bucketName | String | BUCKET_NAME | MinIO 桶名 |
| md5Hash | String | MD5_HASH | 文件 MD5（去重） |
| uploadedBy | String | UPLOADED_BY | 上传人工号 |
| uploadedTime | LocalDateTime | UPLOADED_TIME | 上传时间 |

- `BizFileRel` → 表 `biz_file_rel`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| bizType | String | BIZ_TYPE | 业务类型 |
| bizId | String | BIZ_ID | 业务实体 ID |
| fileObjectId | String | FILE_OBJECT_ID | 文件对象 ID |
| fileRole | String | FILE_ROLE | 文件角色（ATTACHMENT/PHOTO 等） |
| createdTime | LocalDateTime | CREATED_TIME | 创建时间 |

**唯一约束**：`biz_file_rel(BIZ_TYPE, BIZ_ID, FILE_OBJECT_ID)` 联合唯一

**FileApi 接口**：
```java
public interface FileApi {
    FileObjectDTO upload(MultipartFile file, String uploadedBy);
    String getDownloadUrl(String fileId);
    void bindFile(String bizType, String bizId, String fileObjectId, String fileRole);
    List<FileObjectDTO> listBizFiles(String bizType, String bizId);
    void deleteFile(String fileId);
}
```

**FileService**：
- 上传流程：格式/大小白名单校验 → MD5 计算 → 查重（同 MD5 复用已有记录）→ MinIO 上传 → 写 DB
- 存储路径格式：`/{bizType}/{yyyy}/{MM}/{dd}/{uuid}.{ext}`
- 下载：MinIO presigned URL，有效期 1 小时
- 绑定：幂等（联合唯一约束），bindFile 不重复插入
- 删除：逻辑删除 DB 记录，MinIO 对象保留（后续定期清理）

**MinIO 配置**：`MinioConfig` Bean，从 application.yml 读取 endpoint/accessKey/secretKey/bucket

### Job（定时任务管理）

**Entity**:
- `SysJobConf` → 表 `sys_job_conf`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| jobKey | String | JOB_KEY | 任务标识（唯一） |
| jobName | String | JOB_NAME | 任务名称 |
| cronExpr | String | CRON_EXPR | Cron 表达式 |
| status | String | STATUS | ACTIVE/PAUSED |
| allowManualTrigger | Integer | ALLOW_MANUAL_TRIGGER | 0 不允许 / 1 允许 |
| lastRunTime | LocalDateTime | LAST_RUN_TIME | 上次执行时间 |
| nextRunTime | LocalDateTime | NEXT_RUN_TIME | 下次执行时间 |
| createdTime | LocalDateTime | CREATED_TIME | 创建时间 |
| updatedTime | LocalDateTime | UPDATED_TIME | 更新时间 |

- `SysJobRunLog` → 表 `sys_job_run_log`

| 字段 | Java 类型 | 数据库列 | 说明 |
|------|----------|---------|------|
| id | String | ID | 主键，UUID |
| jobId | String | JOB_ID | 任务 ID |
| triggerType | String | TRIGGER_TYPE | SCHEDULED/MANUAL |
| reason | String | REASON | 触发原因 |
| startTime | LocalDateTime | START_TIME | 开始时间 |
| endTime | LocalDateTime | END_TIME | 结束时间 |
| status | String | STATUS | RUNNING/SUCCESS/FAILED |
| errorMsg | String | ERROR_MSG | 错误信息 |
| createdTime | LocalDateTime | CREATED_TIME | 创建时间 |

**JobApi 接口**：
```java
public interface JobApi {
    SysJobConfDTO getJobConf(String jobKey);
    String startJobRun(String jobId, String triggerType, String operatorEmpId);
    void completeJobRun(String runLogId);
    void failJobRun(String runLogId, String errorMsg);
}
```

**JobService**：
- 并发防护：`startJobRun` 检查该 Job 是否有 RUNNING 状态的日志，有则拒绝（GOV-40901）
- 管理：`pauseJob(jobId)`, `resumeJob(jobId)`
- V1 版本：各业务模块自行 `@Scheduled` + `JobApi.startJobRun` 管理状态；架构预留统一调度执行扩展点（JobApi 接口不变，后续 V2 可在 governance 内部加入 cron 调度器统一执行）

**枚举**：`JobStatus`(ACTIVE/PAUSED), `JobRunStatus`(RUNNING/SUCCESS/FAILED)

---

## Phase 4：SQL Probe + Controllers + Config

### SQL Probe（SQL 数据探针）

**SqlProbeService**：
- 执行：`executeSql(String sql, String reason)` — 安全校验链：
  1. 仅允许 SELECT（非 SELECT → GOV-42201）
  2. Schema 白名单校验（可配置允许查询的表/schema）
  3. 强制追加 LIMIT（默认 1000）
  4. 超时控制（默认 30s，`Statement.setQueryTimeout`）
  5. 并发限制（最多 3 个同时执行，`Semaphore` 控制）
- 数据源：独立只读 DataSource（与业务数据源隔离）
- 每次执行写审计日志（高危操作 `BizAction.EXECUTE_SQL`）
- 执行历史：从 `audit_log` 表过滤 `bizAction=EXECUTE_SQL` 获取

### Controllers（全部 8 域）

| Controller | 路径前缀 | @BizAuth |
|-----------|---------|---------|
| DictController | `/api/sys/dicts`(查询) + `/api/admin/sys/dicts`(管理) | SYS_CONFIG |
| ConfigController | `/api/admin/sys/configs` | SYS_CONFIG |
| CalendarController | `/api/admin/sys/calendar` | SYS_CONFIG |
| AuditLogController | `/api/admin/audit-logs` | SYS_CONFIG |
| NotificationController | `/api/notifications`(用户) + `/api/admin/notifications`(管理) | SYS_CONFIG |
| FileController | `/api/files` | 按业务 bizType |
| JobController | `/api/admin/sys/jobs` | SYS_CONFIG |
| SqlProbeController | `/api/admin/sql-probe` | SYS_CONFIG + EXECUTE_SQL |

所有 Controller 使用 `ResponseWrapper<T>` 包装响应，使用 `@Valid` 校验请求体。

### 配置类

- `GovCacheConfig`：RedisTemplate 序列化配置
- `MinioConfig`：MinIO 客户端 Bean（endpoint/accessKey/secretKey/bucket）
- `AutoConfiguration.imports`：注册所有 Config 类

无需独立 Filter/Interceptor，复用 auth-permission-center 的 AuthenticationFilter + AuthorizationInterceptor。

---

## 错误码（GovErrorCode 枚举）

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| GOV-40001 | 404 | 字典类型不存在 |
| GOV-40002 | 404 | 配置项不存在 |
| GOV-40003 | 404 | 日历记录不存在 |
| GOV-40004 | 404 | 任务不存在 |
| GOV-40005 | 404 | 文件不存在 |
| GOV-40006 | 404 | 通知不存在 |
| GOV-40007 | 404 | 任务执行日志不存在 |
| GOV-40301 | 403 | 过去日期不可修改 |
| GOV-40901 | 409 | 字典 dictType+dictCode 重复 |
| GOV-40902 | 409 | 配置 configKey 重复 |
| GOV-40903 | 409 | 任务正在执行中（RUNNING） |
| GOV-42201 | 422 | 非 SELECT SQL 语句 |
| GOV-42202 | 422 | SQL 并发数超限 |
| GOV-42203 | 422 | 文件格式不合法 |
| GOV-42204 | 422 | 文件大小超限 |
| GOV-42205 | 422 | 配置值类型校验失败 |
| GOV-50001 | 500 | MinIO 服务异常 |
| GOV-50002 | 500 | Redis 缓存异常 |
| GOV-50003 | 500 | SQL 执行超时 |

---

## 测试策略

- TDD 驱动：先写失败测试 → 最小实现 → 重构
- Service 层：JUnit 5 + Mockito 单元测试，覆盖核心业务逻辑
- Facade 层：验证 API 契约实现的正确性
- Controller 层：MockMvc 端到端测试（`MockMvcBuilders.standaloneSetup`）
- 每个 Phase 完成后独立 commit
