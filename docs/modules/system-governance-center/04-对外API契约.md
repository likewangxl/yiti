# 系统治理中心对外 API 契约

本文件对应 `system-governance-center/src/main/java/com/bank/branch/platform/governance/api` 的公开 Bean 接口。调用方只能依赖这些接口和 `api.dto` 中的 DTO，不得引用治理实体、Mapper 或内部 Service。方法均为同 JVM 同步调用，异常按统一业务异常向调用方传播。

## 1. 字典 `DictApi`

```java
List<DictItemDTO> getDictItems(String dictType);
Optional<DictItemDTO> getDictItem(String dictType, String dictCode);
String getDictLabel(String dictType, String dictCode);
Map<String, List<DictItemDTO>> batchGetDictItems(Set<String> dictTypes);
boolean isValidDictValue(String dictType, String dictValue);
```

- 查询只返回启用项并按 `sort_order` 升序；类型不存在或没有启用项时返回空列表/空 `Optional`。
- `getDictLabel` 找不到标签时返回原 `dictCode`；`isValidDictValue` 同时要求项存在且启用。
- 批量方法以 `dictType` 为 key，空集合返回空映射。
- 实现使用治理中心的进程内 Caffeine 缓存；管理端写操作清除受影响类型的缓存。

`DictItemDTO` 字段：`id`、`dictType`、`dictCode`、`dictLabel`、`dictValue`、`sortOrder`。

## 2. 工作日历 `CalendarApi`

```java
boolean isWorkingDay(LocalDate date);
int countWorkingDays(LocalDate from, LocalDate to);
List<CalendarDayDTO> getWorkingDays(int year);
LocalDate addWorkingDays(LocalDate from, int workingDays);
```

- `date`、`from` 或 `to` 为空时抛 `IllegalArgumentException`；`from >= to` 的计数结果为 `0`。
- `isWorkingDay` 命中 `SYS_CALENDAR_DAY` 记录时使用记录值，无记录时按工作日历默认周规则判断。
- `getWorkingDays` 返回指定年份已有记录的 `List`，不是 `Set`。
- `addWorkingDays` 从起始日期的下一天开始向后计数；Facade 拒绝空日期和 `workingDays == 0`，调用方应传正数。

`CalendarDayDTO` 字段：`day`（`LocalDate`）、`isWorkday`（`Integer`，1/0）、`remark`。

## 3. 系统配置 `ConfigApi`

```java
Optional<String> getConfigValue(String configKey);
String getConfigValue(String configKey, String defaultValue);
<T> T getConfigValue(String configKey, Class<T> type);
```

- 原始读取只返回启用配置；缺失时 `Optional.empty()` 或返回调用方给定的默认值。
- 强类型读取依据 `SYS_CONFIG_KV.value_type` 转换：`STRING`、`NUMBER`、`BOOL` 和 `JSON`。缺失抛 `GOV-40002`，类型不匹配抛 `GOV-42205`。
- 配置读取使用进程内 Caffeine 缓存；管理端更新后清除对应键。调用方不得把敏感配置写入日志或审计明文。
- 该 API 没有写方法；写入只能通过管理端配置接口和其权限/审计边界完成。

## 4. 任务调度 `JobApi`

```java
Optional<JobConfDTO> getJobConf(String jobKey);
String registerJob(RegisterJobCmd cmd);
void unregisterJob(String jobKey);
JobTriggerRespDTO triggerJobByKey(String jobKey, String triggerType, String reason,
                                  String dataDate, String allocDate, String operatorEmpId);
boolean isJobRunning(String jobKey);
```

### 4.1 配置读取与注册

- `getJobConf` 找不到任务时返回 `Optional.empty()`；其他调用异常由 Facade 按实现策略处理。
- `registerJob` 先校验可选配置 `governance.scheduler.allowed-job-keys`；集合非空且不包含 `jobKey` 时返回 `GOV-40303`，不写入/更新 `SYS_JOB_CONF`。通过门禁后再校验 Cron 和 `quartzJobClass`，并在事务中写入/更新配置；同 `jobKey` 为覆盖语义；Scheduler 可用时同步注册 JobDetail/CronTrigger，不可用时仅写配置。
- `RegisterJobCmd` 字段为 `jobKey`、`jobName`、`cronExpr`、`quartzJobClass`（必填语义）、`jobData`（透传 JobDataMap）、`misfirePolicy`（默认 `FIRE_ONCE_NOW`）、`allowManualTrigger`（默认 true）、`remark`。
- `unregisterJob` 尝试解除 Quartz Job 后删除配置记录；Scheduler 删除失败记录日志并继续完成配置删除，调用方仍应检查运行状态。

### 4.2 触发与运行状态

- `triggerType` 为空按 `MANUAL` 处理，只接受 `MANUAL`/`AUTO`；两者都受 `allowManualTrigger` 保护。
- 治理层再次检查 `jobKey` 对应配置、可选允许集合和 Scheduler；手动触发的理由由调用方/REST 参数校验保证，`dataDate`、`allocDate` 可选并透传给 Job。按 ID 的管理端触发同样在 Service 层解析 JobKey 后执行该门禁。
- 成功返回 `JobTriggerRespDTO`；`runLogId` 可能为空，因为执行日志由 Quartz 全局监听器异步创建。找不到任务为 `GOV-40004`，禁止触发为 `GOV-40302`，不在 Scheduler 允许集合为 `GOV-40303`，Scheduler 失败为 `GOV-50004`。
- `isJobRunning` 按 `jobKey` 查询 `SYS_JOB_RUN_LOG` 的 `RUNNING` 记录；任务不存在返回 `false`。

### 4.3 Scheduler JobKey 允许集合

`governance.scheduler.allowed-job-keys` 由运行 profile 提供，值为 JobKey 字符串集合。未设置或为空表示不启用限制并保持正式环境原有行为；非空时仅允许集合内任务进入 Quartz。该门禁覆盖启动同步、`registerJob`、`triggerJobByKey`、管理端按 ID 触发、恢复和底层 JobDetail/Trigger 注册；拒绝发生在配置状态写入、运行日志写入和 Quartz 调用前。

示例（隔离大屏 profile）：

```yaml
governance:
  scheduler:
    allowed-job-keys:
      - BRANCH_DASHBOARD_BATCH
```

`JobConfDTO` 字段：`id`、`jobKey`、`jobName`、`cronExpr`、`status`（`ACTIVE/PAUSED`）、`allowManualTrigger`、`lastRunTime`、`nextRunTime`、`remark`。

`JobTriggerRespDTO` 字段：`jobId`、`triggerType`、`runLogId`、`jobKey`、`triggerTime`。

## 5. 审计 `AuditApi`

```java
void log(AuditLogCmd cmd);
PageResult<AuditLogDTO> queryLogs(AuditLogQueryReqDTO query, PageRequest page);
```

- `log` 同步写入 `AUDIT_LOG`，Service 使用 `Propagation.REQUIRES_NEW`；理由、操作人和业务动作应由调用方按高危操作要求提供。
- `queryLogs` 支持工号、业务类型、业务动作、起止日期和关键词筛选，返回 `PageResult`。查询必须在调用方的统一数据范围内执行。
- 审计记录为追加型数据，调用方不能通过该 API 修改或删除。

`AuditLogCmd` 字段：`traceId`、`empId`、`empName`、`bizType`、`bizAction`、`resourceUrl`、`requestMethod`、`requestParams`、`responseStatus`、`errorMsg`、`ipAddress`、`userAgent`、`executionTime`、`reason`、`targetType`、`targetId`、`beforeSnapshot`、`afterSnapshot`、`addedItems`、`removedItems`。请求参数、错误和快照须在进入 API 前脱敏。

`AuditLogDTO` 字段：`id`、`traceId`、`empId`、`empName`、`bizType`、`bizAction`、`resourceUrl`、`requestMethod`、`requestParams`、`responseStatus`、`errorMsg`、`ipAddress`、`executionTime`、`reason`、`targetType`、`targetId`、`beforeSnapshot`、`afterSnapshot`、`addedItems`、`removedItems`、`createdTime`。

`AuditLogQueryReqDTO` 字段：`empId`、`bizType`、`bizAction`、`startTime`、`endTime`、`keyword`，均为可选字符串。

## 6. 通知 `NotifyApi`

```java
void sendNotification(NotificationCmd cmd);
void batchSendNotifications(List<NotificationCmd> cmds);
int countUnread(String empId);
PageResult<NotificationDTO> queryNotifications(String empId, Boolean isRead, PageRequest page);
```

- `sendNotification` 同步插入通知，`notifyType` 为空时使用 `SYSTEM`；调用方如需事务提交后发送，应在调用方事务中使用事务事件监听器。
- 批量发送逐条执行；单条失败记录服务端日志并继续，不回滚其他已发送条目。
- 查询和未读计数严格按传入 `empId`；`isRead=null` 查询全部，`true`/`false` 分别查询已读/未读。

`NotificationCmd` 字段：`targetEmpId`、`title`、`content`、`notifyType`、`bizType`、`bizId`、`linkUrl`。

`NotificationDTO` 字段：`id`、`empId`、`title`、`content`、`notifyType`、`notifyTypeLabel`、`bizType`、`bizId`、`linkUrl`、`isRead`、`readTime`、`createdTime`。

## 7. 文件 `FileApi`

```java
FileObjectDTO upload(MultipartFile file, String uploadedBy);
FileObjectDTO upload(MultipartFile file, String uploadedBy, String category);
FileObjectDTO upload(byte[] bytes, String filename, String contentType,
                     String uploadedBy, String category);
byte[] getFileContent(String fileId);
void writeFileContent(String fileId, OutputStream outputStream);
String getDownloadUrl(String fileId);
void bindFile(String bizType, String bizId, String fileObjectId, String fileRole);
void unbindFile(String bizType, String bizId, String fileObjectId);
List<FileObjectDTO> listBizFiles(String bizType, String bizId);
void deleteFile(String fileId);
String getFileName(String fileId);
Map<String, Long> getFileSizes(List<String> fileIds);
Map<String, String> getFileNames(List<String> fileIds);
```

- 上传校验扩展名和 50 MB 大小上限，按 MD5 去重；命中时返回已有对象并将 `newlyCreated=false`。
- 未命中时先写 OBS 再落库；元数据落库失败会尝试清理本次新对象。`category` 只影响 OBS 对象 key 前缀。
- `getFileContent` 返回字节；`writeFileContent` 写入调用方流但不关闭调用方流；`getDownloadUrl` 返回受配置时效限制的 OBS 预签名 URL。
- `bindFile` 对相同业务三元组幂等；`unbindFile` 只解除关联；`listBizFiles` 按关联创建时间升序返回。
- `deleteFile` 删除对象、元数据和全部关联，当前不做引用计数；调用方须先确保文件没有其他业务用途。
- `getFileName` 找不到 ID 时返回 `"file"`；批量元数据查询对空入参返回空映射，找不到的 ID 不出现在结果中。

`FileObjectDTO` 字段：`id`、`fileName`、`fileSize`、`fileType`、`md5Hash`、`fileRole`、`uploadedBy`、`uploadedTime`、`newlyCreated`。`newlyCreated` 仅是上传结果标记，不对应数据库列。

## 8. 员工/机构标签 `PersonTagApi`

```java
List<String> getUsernamesByTagIds(List<Long> tagIds);
List<String> getDeptNosByTagIds(List<Long> tagIds);
List<PersonTagDTO> getTagsByIds(List<Long> tagIds);
List<PersonTagDTO> getTagsByNames(List<String> tagNames);
```

- `tagIds`/`tagNames` 为空返回空列表；结果去重，未命中的标签不贡献成员。
- `getUsernamesByTagIds` 只返回 `EMP` 维度的 `PT_USER.USERNAME`；`getDeptNosByTagIds` 只返回 `ORG` 维度的 `EXT_ORG_INFO.DEPT_NO`。
- 返回标签 DTO 只暴露标识和名称，缺少的 ID/名称不补造对象。

`PersonTagDTO` 字段：`tagId`、`tagName`。

## 9. 事务、线程与依赖要求

- 上述 API 不建立远程调用协议；调用方不得假设治理内部实体或表结构可以直接使用。
- 字典、配置和年度日历缓存是单实例本地缓存，写后由治理服务清除；跨模块调用方不应持有缓存副本作为权威状态。
- 调度执行日志由 Quartz 全局 `JobExecutionLogger` 统一写入，业务模块不得通过私有 Mapper 或旧式手工日志流程绕过 `JobApi`。
- 文件 API 涉及 OBS 外部副作用；`obs.enabled=false` 时对象操作失败并返回 `GOV-50301`。调用方需根据 `newlyCreated` 决定是否执行安全补偿。
- 高危调用应显式传递操作人、理由和目标信息，确保 `AuditApi` 记录完整、脱敏且可追踪。
