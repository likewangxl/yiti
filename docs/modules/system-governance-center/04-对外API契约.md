# 系统治理中心 -- 对外 API 契约

> 本文档定义 governance 模块提供给其他业务模块调用的 Java 接口契约。
> 所有 Api 接口在模块化单体中为本地方法调用（Spring Bean 注入），无 RPC 开销。
> 所有 Api 方法都是同步强依赖（S），除 NotifyApi.sendNotification() 允许异步（事务提交后执行）。

> **2026-07-19 回填说明**：本次以 `system-governance-center` 源码（`api/`/`facade/`/`service/`/`enums/`/`storage/`/`config/`）为唯一依据，对 2026-04-03 版做就地订正，主要包括：
> 1. **JobApi 全量重写**：`startJobRun`/`completeJobRun`/`failJobRun` 三方法已在 V1.6 quartz 整合中删除（写日志改由全局 `JobExecutionLogger` 统一处理），当前接口仅剩 `getJobConf`（只读）+ V1.7 新增的 `registerJob`/`unregisterJob`（声明式注册/注销），本文历史版本描述的三方法已不存在于源码；
> 2. **FileApi 补齐全部方法**：历史版本只收录部分方法，现按源码补齐上传重载、流式读取、文件元数据批量查询；2026-08-28 新增 `unbindFile`，用于仅解除指定业务关联；
> 3. **错误码全量核对**：按 `GovErrorCode` 枚举源码逐条核对本文出现的每个错误码，`FileApi.upload()` 相关错误码订正为 `GOV-42203`/`GOV-42204`/`GOV-40005`（原文误写 `GOV-40903`/`GOV-40904`/`GOV-40404`）；
> 4. **MinIO → 华为云 OBS**：文件存储已切换为 `storage/ObsStorageClient`（`com.obs.services.ObsClient`），配置键 `obs.*`；本文相应措辞已订正（代码 Javadoc 与 `GovErrorCode.MINIO_ERROR` 枚举常量名/文案仍残留 "MinIO" 字样属历史命名，非文档笔误，已在 FileApi 一节注明）；预签名下载 URL 默认有效期订正为 **10 分钟**（`obs.presignExpireSeconds` 默认 600 秒，非原文"1 小时"）；
> 5. **缓存后端 Redis → 本地内存缓存**：`DictApi`/`ConfigApi`/`CalendarApi` 内部缓存已从 Redis 切换为 `config/MemoryCacheService`（Caffeine 单实例，`maximumSize=10000`，`expireAfterWrite=1小时` 全局固定），本文原描述的"TTL 10 分钟"/"TTL 24 小时"等分级 TTL 已不准确，已订正为统一口径。
>
> 本说明仅记录本轮回填的口径依据，不重复维护版本历史；后续变更请直接更新对应小节正文。

---

## 1. DictApi -- 字典服务API

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.DictItemDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 字典服务对外API
 * 提供字典数据的查询与校验能力
 * 高频调用，内部启用本地内存缓存（MemoryCacheService/Caffeine，全局 expireAfterWrite=1小时，订正：已非 Redis）
 */
public interface DictApi {

    /**
     * 获取指定类型的字典项列表（仅启用状态）
     * 按 sort_order 升序排列
     *
     * @param dictType 字典类型编码（如 INDUSTRY、CUSTOMER_TYPE）
     * @return 字典项列表，dictType 不存在时返回空列表
     */
    List<DictItemDTO> getDictItems(String dictType);

    /**
     * 获取指定字典项
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典项，不存在时返回 Optional.empty()
     */
    Optional<DictItemDTO> getDictItem(String dictType, String dictCode);

    /**
     * 获取指定字典项的显示标签
     * 用于将存储值转换为显示文本
     *
     * @param dictType 字典类型编码
     * @param dictCode 字典项编码
     * @return 字典标签（如 "信息技术"），不存在时返回 dictCode 本身
     */
    String getDictLabel(String dictType, String dictCode);

    /**
     * 批量获取多个类型的字典项
     * 减少多次调用开销，一次性获取多个字典类型的数据
     *
     * @param dictTypes 字典类型集合
     * @return key=dictType, value=该类型下的字典项列表
     */
    Map<String, List<DictItemDTO>> batchGetDictItems(Set<String> dictTypes);

    /**
     * 校验字典值是否合法（存在且启用）
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典值
     * @return true=合法, false=不合法（不存在或已禁用）
     */
    boolean isValidDictValue(String dictType, String dictValue);
}
```

**调用约束：**
- 所有方法均为同步调用，内部走本地内存缓存（`MemoryCacheService`/Caffeine，全局 `expireAfterWrite=1小时`；订正：已非 Redis，详见文首回填说明第 5 条）
- `getDictItems()` 和 `batchGetDictItems()` 频率预估：每次页面加载 1-5 次
- `getDictLabel()` 频率预估：列表渲染时每行 1-3 次（建议调用方批量获取后本地查找）
- `isValidDictValue()` 用于表单提交校验，频率较低
- dictType 不存在时不抛异常，返回空结果

---

## 2. CalendarApi -- 工作日历API

```java
package com.bank.branch.platform.governance.api;

import java.time.LocalDate;
import java.util.Set;

/**
 * 工作日历对外API
 * 提供工作日判断与工作日计算能力
 * 高频调用，内部启用本地内存缓存（MemoryCacheService/Caffeine，全局 expireAfterWrite=1小时，订正：已非 Redis）
 */
public interface CalendarApi {

    /**
     * 判断指定日期是否工作日
     *
     * @param date 日期
     * @return true=工作日, false=休息日
     * @throws IllegalArgumentException date 为 null 时
     */
    boolean isWorkingDay(LocalDate date);

    /**
     * 计算两个日期之间的工作日天数（不含起始日，包含结束日）
     * 用于工作流红绿灯超时计算
     *
     * @param from 起始日期（不含）
     * @param to   结束日期（包含）
     * @return 工作日天数，from >= to 时返回 0
     * @throws IllegalArgumentException from 或 to 为 null 时
     */
    int countWorkingDays(LocalDate from, LocalDate to);

    /**
     * 获取某年全部工作日集合
     * 用于绩效计算窗口
     *
     * @param year 年份（如 2026）
     * @return 该年所有工作日的日期集合
     * @throws IllegalArgumentException year 小于 2020 时
     */
    Set<LocalDate> getWorkingDays(int year);

    /**
     * 从指定日期开始，推算 N 个工作日后的日期
     * 用于工作流截止日期计算
     *
     * @param from        起始日期
     * @param workingDays 要推算的工作日天数（正数向后，负数向前）
     * @return 推算后的日期
     * @throws IllegalArgumentException from 为 null 或 workingDays 为 0 时
     */
    LocalDate addWorkingDays(LocalDate from, int workingDays);
}
```

**调用约束：**
- 所有方法均为同步调用，内部走本地内存缓存（`MemoryCacheService`/Caffeine，全局 `expireAfterWrite=1小时`；订正：已非 Redis）
- `isWorkingDay()` 频率预估：红绿灯更新时每条流程调用 1 次
- `countWorkingDays()` 频率预估：红绿灯计算时高频（每 30 分钟批量调用）
- `addWorkingDays()` 频率预估：流程发起时每次调用 1 次
- `getWorkingDays()` 频率预估：低频，绩效计算时使用

---

## 3. JobApi -- 任务调度API

> **订正（2026-07-19）**：以下为 V1.6 quartz 整合后精简 + V1.7 声明式注册扩展后的**当前真实接口**。原 V1.0-V1.5 版本持有的 `startJobRun`/`completeJobRun`/`failJobRun` 三个写日志方法**已从接口删除**——写日志改由全局 Quartz `JobListener`（`listener/JobExecutionLogger`）在 Job 生命周期回调中统一处理，业务模块的 `@Scheduled` 任务不再需要显式上报执行状态（历史遗留原因：这三个方法目前仍以同名 `public` 方法保留在 `service/JobService` 内部供 `JobController` 内部逻辑复用，但**不再通过 `JobApi` 对外暴露**，其他模块无法再调用）。

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;

import java.util.Optional;

/**
 * 任务调度对外 API（V1.6 quartz 整合后精简）.
 *
 * <p>仅保留 {@link #getJobConf} 一个只读查询方法，供业务模块在需要时
 * 查询 sys_job_conf 中的任务配置；V1.7 新增 {@link #registerJob}/{@link #unregisterJob}
 * 供业务模块声明式注册/注销调度任务。
 */
public interface JobApi {

    /**
     * 获取任务配置.
     *
     * @param jobKey 任务唯一标识（如 PERF_DAILY_CALC）
     * @return 任务配置，不存在时返回 Optional.empty()
     */
    Optional<JobConfDTO> getJobConf(String jobKey);

    /**
     * V1.7 新增：注册（或覆盖）一个调度任务.
     *
     * <p>原子写入 sys_job_conf 一行 + Quartz Scheduler 注入 JobDetail/CronTrigger。
     * 若 jobKey 已存在则覆盖（cron 变更场景）。
     * 若 Scheduler 不可用（测试上下文）则仅写 sys_job_conf，不抛异常。
     *
     * @param cmd 注册参数
     * @return 写入后 sys_job_conf 主键 id
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50010 cron 非法
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50011 quartz_job_class 反射失败
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50012 Scheduler 注册失败
     */
    String registerJob(RegisterJobCmd cmd);

    /**
     * V1.7 新增：注销一个调度任务（幂等）.
     *
     * <p>存在 Scheduler 时先 {@code scheduler.deleteJob()}（JobGroup 按 jobKey 前缀推断：
     * `PERF_METRIC_` 前缀用 `PERF_METRIC` group，其余用 `DEFAULT`），随后无条件
     * {@code jobConfMapper.deleteByJobKey()} 物理删除 sys_job_conf 行（不是暂停/软删）。
     *
     * @param jobKey 任务唯一标识
     */
    void unregisterJob(String jobKey);
}
```

**调用约束：**
- 业务模块通过 `registerJob(RegisterJobCmd)` 声明式注册定时任务，典型用法（以 performance-engine-center 为例）：

```java
// 调用方使用示例：应用启动时（如 @PostConstruct 或 ApplicationReadyEvent）声明式注册
RegisterJobCmd cmd = new RegisterJobCmd();
cmd.setJobKey("PERF_DAILY_CALC");
cmd.setJobName("绩效每日计算");
cmd.setCronExpr("0 0 2 * * ?");
cmd.setQuartzJobClass("com.bank.branch.platform.performance.job.DailyCalcQuartzJob");
cmd.setAllowManualTrigger(true);
jobApi.registerJob(cmd);
```

- 定时任务的实际执行体是一个独立的 `QuartzJobBean` 子类（`quartzJobClass` 指定的全限定名），由 Quartz 反射创建并调度，**不是**调用方自己维护的 `@Scheduled` 方法
- 执行状态（RUNNING/SUCCESS/FAILED）由全局 `JobExecutionLogger`（Quartz `JobListener`）在 `jobToBeExecuted`/`jobWasExecuted` 回调中统一写入 `sys_job_run_log`，调用方无需再显式上报
- `registerJob()` 对同一 `jobKey` 重复调用是**覆盖**语义（cron/class/misfire 策略变更场景），非新增
- `unregisterJob()` 是**物理删除** `sys_job_conf` 行 + 尝试从 Quartz 摘除 Job，并非暂停

---

## 4. AuditApi -- 审计日志API

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;

/**
 * 审计日志对外API
 * 提供审计日志的写入与查询能力
 * 写入为同步操作，确保审计数据与业务操作一致
 */
public interface AuditApi {

    /**
     * 记录审计日志
     * 同步写入 audit_log 表
     *
     * @param cmd 审计日志写入命令
     * @throws IllegalArgumentException cmd 中必填字段缺失时
     */
    void log(AuditLogCmd cmd);

    /**
     * 查询审计日志（分页）
     *
     * @param query 查询条件
     * @param page  分页参数
     * @return 分页审计日志列表
     */
    PageResult<AuditLogDTO> queryLogs(AuditLogQueryReqDTO query, PageRequest page);
}
```

**调用约束：**
- `log()` 为同步写入，不走异步消息队列
- `log()` 使用 `@Transactional(propagation = REQUIRES_NEW)` 独立事务，确保即使业务事务回滚，审计日志仍保留
- 频率预估：每次高危操作调用 1 次（EXPORT/IMPORT/DELETE/TRANSFER/EXECUTE_SQL 等）
- 通常由 common-aop 中的 `AuditLogAspect` 自动调用，业务模块也可手动调用

---

## 5. NotifyApi -- 通知消息API

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;

import java.util.List;

/**
 * 通知消息对外API
 * 提供通知的发送与查询能力
 * sendNotification 允许异步（事务提交后执行）
 */
public interface NotifyApi {

    /**
     * 发送通知给指定用户
     * 允许在事务提交后异步执行（使用 @TransactionalEventListener）
     *
     * @param cmd 通知发送命令
     * @throws IllegalArgumentException targetEmpId 或 title 为空时
     */
    void sendNotification(NotificationCmd cmd);

    /**
     * 批量发送通知
     * 用于流程审批后通知多个相关人员
     *
     * @param cmds 通知发送命令列表
     */
    void batchSendNotifications(List<NotificationCmd> cmds);

    /**
     * 查询用户未读通知数量
     * 用于工作台未读通知卡片
     *
     * @param empId 用户工号
     * @return 未读通知数量
     */
    int countUnread(String empId);

    /**
     * 查询用户通知列表（分页）
     *
     * @param empId  用户工号
     * @param isRead 是否已读（null=全部, true=已读, false=未读）
     * @param page   分页参数
     * @return 分页通知列表
     */
    PageResult<NotificationDTO> queryNotifications(String empId, Boolean isRead, PageRequest page);
}
```

**调用约束：**
- `sendNotification()` 允许异步执行（在业务事务提交后），避免事务回滚导致"幽灵通知"
- `batchSendNotifications()` 内部逐条调用 `sendNotification()`，失败不影响其他通知的发送
- `countUnread()` 频率预估：工作台加载时每用户调用 1 次
- 调用方应确保 `targetEmpId` 为有效工号（不做额外校验，无效工号的通知无人可见）

---

## 6. ConfigApi -- 系统配置API

```java
package com.bank.branch.platform.governance.api;

import java.util.Optional;

/**
 * 系统配置对外API
 * 提供系统级键值配置的读取能力
 * 高频调用，内部启用本地内存缓存（MemoryCacheService/Caffeine，全局 expireAfterWrite=1小时，订正：已非 Redis）
 */
public interface ConfigApi {

    /**
     * 获取配置值
     *
     * @param configKey 配置键
     * @return 配置值，不存在时返回 Optional.empty()
     */
    Optional<String> getConfigValue(String configKey);

    /**
     * 获取配置值（带默认值）
     * 配置项不存在时返回指定默认值
     *
     * @param configKey    配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    String getConfigValue(String configKey, String defaultValue);

    /**
     * 获取配置值并转为指定类型
     * 支持 Integer、Long、Boolean、String
     *
     * @param configKey 配置键
     * @param type      目标类型
     * @param <T>       目标类型参数
     * @return 转换后的配置值
     * @throws com.bank.branch.platform.common.web.exception.BizException 配置项不存在时抛 GOV-40002（GovErrorCode.CONFIG_NOT_FOUND；订正：原文误写 GOV-40401，该码不是任何已定义错误码）；
     *         类型转换失败时抛 GOV-42205（GovErrorCode.CONFIG_VALUE_TYPE_INVALID；订正：原文未提及此码）
     * @throws ClassCastException 类型转换失败时
     */
    <T> T getConfigValue(String configKey, Class<T> type);
}
```

**调用约束：**
- 所有方法均为同步调用，内部走本地内存缓存（`MemoryCacheService`/Caffeine，全局 `expireAfterWrite=1小时`；订正：已非 Redis）
- 建议在模块启动时通过 `getConfigValue(key, defaultValue)` 加载配置并缓存到本地变量
- 配置变更后本地内存缓存会被主动清除（`evict()`），下次调用会重新加载
- 频率预估：模块初始化时高频，运行时低频

---

## 7. FileApi -- 文件管理API

> **订正（2026-08-28）**：本文按 `api/FileApi.java` 的 13 个方法维护；本轮新增 `unbindFile`。

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件管理对外API
 * <p>
 * 提供统一的文件上传/下载/关联管理能力。
 * 文件存储在华为云 OBS 对象存储中（订正：非 MinIO，见 storage/ObsStorageClient；
 * 代码 Javadoc 中仍残留 "MinIO" 字样属历史注释未清理，以实现为准）。
 * </p>
 */
public interface FileApi {

    /**
     * 上传文件到 OBS（兼容旧调用，category 默认 FileCategory.GENERAL）
     * 内部进行格式白名单校验和大小限制校验，支持 MD5 去重
     *
     * @param file       文件（MultipartFile）
     * @param uploadedBy 上传人工号
     * @return 文件对象信息
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-42203 文件格式不在白名单（订正：原文误写 GOV-40903，该码实为 TASK_ALREADY_RUNNING）
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-42204 文件大小超限（订正：原文误写 GOV-40904）
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50001 OBS 存储异常（枚举常量名仍为 MINIO_ERROR，见上）
     */
    FileObjectDTO upload(MultipartFile file, String uploadedBy);

    /**
     * 上传文件到 OBS，带类型前缀 category（OBS 对象名形如 {yyyy/MM/dd}/{prefix}_{uuid}.{ext}）。
     * 本文历史版本未收录该方法。
     *
     * @param file       文件
     * @param uploadedBy 上传人工号
     * @param category   类型前缀，见 storage.FileCategory（如 ANNOUNCEMENT="gg"、EXPORT_KPI="jxkpi" 等）
     * @return 文件对象信息
     */
    FileObjectDTO upload(MultipartFile file, String uploadedBy, String category);

    /**
     * 字节直传到 OBS（导出/导入/公告等无 MultipartFile 场景）。本文历史版本未收录该方法。
     *
     * @param bytes       文件内容
     * @param filename    原始文件名（用于取扩展名与展示）
     * @param contentType MIME 类型
     * @param uploadedBy  上传人工号
     * @param category    类型前缀，见 storage.FileCategory
     * @return 文件对象信息
     */
    FileObjectDTO upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category);

    /**
     * 读取文件字节内容（供业务模块自行流式下载，替代原 getFilePath 本地读盘）。
     * 本文历史版本未收录该方法；当前 FileController 的 REST 下载端点即基于此方法实现直接字节流响应
     * （不再是 302 重定向到预签名 URL，见 03 文档 G.2）。
     *
     * @param fileId 文件对象ID
     * @return 文件字节内容
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40005 文件不存在（订正：原文误写 GOV-40404）
     */
    byte[] getFileContent(String fileId);

    /**
     * 将 OBS 文件内容流式写入调用方输出流，不关闭调用方输出流。
     *
     * @param fileId 文件对象 ID
     * @param outputStream 调用方输出流
     */
    void writeFileContent(String fileId, OutputStream outputStream);

    /**
     * 获取文件下载URL（OBS 预签名临时 URL，订正：默认有效期 10 分钟——obs.presignExpireSeconds 默认 600 秒，非原文"1 小时"）
     *
     * @param fileId 文件对象ID
     * @return 预签名下载 URL
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40005 文件不存在（订正：原文误写 GOV-40404）
     */
    String getDownloadUrl(String fileId);

    /**
     * 关联文件到业务对象
     * 在 biz_file_rel 表中创建关联记录
     *
     * @param bizType      业务类型（如 LEAD、CUSTOMER）
     * @param bizId        业务ID
     * @param fileObjectId 文件对象ID
     * @param fileRole     文件用途（ATTACHMENT / PHOTO / ...），可为 null
     */
    void bindFile(String bizType, String bizId, String fileObjectId, String fileRole);

    /**
     * 解除指定业务对象与文件的关联；不删除 FILE_OBJECT、OBS 对象或该文件的其他业务关联。
     * 入参为空时抛 IllegalArgumentException；目标关系不存在时按幂等成功处理。
     */
    void unbindFile(String bizType, String bizId, String fileObjectId);

    /**
     * 查询业务关联的文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 文件对象列表（按 created_time 升序）
     */
    List<FileObjectDTO> listBizFiles(String bizType, String bizId);

    /**
     * 删除文件
     * 解除所有 biz_file_rel 关联，删除 file_object 记录
     * 订正：当前实现无条件删除 OBS 实际文件，不做"是否还有其他业务引用"的引用计数判断（详见 03 文档 G.4）
     *
     * @param fileId 文件对象ID
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40005 文件不存在（订正：原文误写 GOV-40404）
     */
    void deleteFile(String fileId);

    /**
     * 获取原始文件名，供 Content-Disposition 响应头使用。本文历史版本未收录该方法。
     *
     * @param fileId 文件对象ID
     * @return 原始文件名；id 不存在时返回 "file"（不抛异常）
     */
    String getFileName(String fileId);

    /**
     * 批量获取文件大小（字节）。本文历史版本未收录该方法。
     *
     * @param fileIds 文件对象ID列表
     * @return id -> fileSize 映射；不存在的 id 不在结果中
     */
    Map<String, Long> getFileSizes(List<String> fileIds);

    /**
     * 批量获取文件名（供列表展示附件名/tooltip，避免逐行 getFileName 的 N+1）。本文历史版本未收录该方法。
     *
     * @param fileIds 文件对象ID列表
     * @return id -> fileName 映射；入参为空返回空映射，不存在的 id 不在结果中
     */
    Map<String, String> getFileNames(List<String> fileIds);
}
```

**调用约束：**
- `upload()` 系列方法为同步操作；`MultipartFile` 重载使用两次输入流，第一次流式计算 MD5，第二次流式上传 OBS，不再调用 `getBytes()`；`byte[]` 重载保留兼容
- `FileObjectDTO.newlyCreated` 是不映射数据库的补偿标记：本次新建文件对象为 `true`，MD5 命中既有共享对象为 `false`；调用方只能补偿删除本次新建对象
- `getFileContent()`：REST 层 `/api/files/{fileId}/download` 直接调用本方法读出全部字节后写响应流，**不做分片/断点续传**，大文件需调用方自行评估内存占用
- `writeFileContent()`：直接将 OBS 对象输入流复制到调用方 `OutputStream`，只关闭 OBS 输入流，不关闭调用方输出流，适用于大文件 HTTP 流式下载
- `getDownloadUrl()` 返回的预签名 URL 默认有效期 10 分钟（`obs.presignExpireSeconds`，可配置），调用方应在 URL 过期前使用
- `bindFile()` 幂等：相同的 `bizType + bizId + fileObjectId` 不会重复创建关联
- `unbindFile()` 只按 `bizType + bizId + fileObjectId` 删除 `BIZ_FILE_REL` 中的目标关系；关系不存在时
  不报错，也不删除 `FILE_OBJECT`、OBS 对象或其他业务对象的关联。它用于附件替换等场景，不能以
  `deleteFile()` 代替
- `deleteFile()` 需要注意：**当前实现无引用计数判断**，调用即无条件删除 OBS 物理文件 + `file_object` 记录 + 该文件全部业务关联，如果文件仍被其他业务对象引用会一并失效，调用方需自行确认没有其他引用后再调用（订正：原文描述的"若无其他引用则删除"逻辑当前源码未实现）
- `getFileSizes()`/`getFileNames()` 内部使用 MyBatis-Plus `BaseMapper.selectBatchIds` 一次查出，避免 N+1
- `upload()` 的格式白名单与大小上限**当前为 `FileService` 内硬编码常量**（扩展名白名单集合 + `MAX_FILE_SIZE=50MB`），订正：并非原文描述的由 `sys_config_kv`（`file.upload.allowed.types`/`file.upload.max.size.mb`）读取——`sys_config_kv` 中若存在同名配置项，当前代码并未接入读取

---

## 8. PersonTagApi -- 人员标签API（2026-07-20 新增）

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.PersonTagDTO;

import java.util.List;

/**
 * 人员标签对外 API。
 * 供其他业务模块按标签圈定员工范围，以及回显/校验标签名称。
 */
public interface PersonTagApi {

    /** 按标签 ID 集合取其下全部员工工号（多标签取并集、去重）。 */
    List<String> getUsernamesByTagIds(List<Long> tagIds);

    /** 按标签 ID 集合查标签（用于存在性校验与名称回显；缺失的 ID 即已删除标签）。 */
    List<PersonTagDTO> getTagsByIds(List<Long> tagIds);

    /** 按标签名称集合查标签（Excel 导入按名称解析 ID 用）。 */
    List<PersonTagDTO> getTagsByNames(List<String> tagNames);
}
```

**实现**：`facade/PersonTagFacade`（仅做实体→DTO 转换与去重，无业务规则）。

**语义与注意事项：**

| 项 | 说明 |
|---|---|
| 工号口径 | 返回的是 `PT_USER.USERNAME`（工号），**不是** `USER_ID` 代理键；调用方无需再转换 |
| 空入参 | `tagIds`/`tagNames` 为 null 或空 → 返回空列表，不查库 |
| 标签不存在 | 不抛异常：`getUsernamesByTagIds` 该标签不贡献工号；`getTagsByIds` 结果中缺失该 ID，调用方据此判定"范围失效" |
| 去重 | ID/名称入参保序去重后再查；多标签成员并集去重 |
| 查询次数 | 均为单次 IN 查询，无 N+1 |

**当前调用方**：`performance-engine-center` 的 KPI 方案「员工标签范围」
（`KpiSchemeService` 保存校验、`KpiScoreCalcService` 计分范围解析、`KpiSchemeImportStrategy` 导入按名称解析）。

---

## 9. DTO 定义

### 9.1 DictItemDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 字典项传输对象
 */
@Data
public class DictItemDTO {

    /** 字典ID */
    private String id;

    /** 字典类型编码 */
    private String dictType;

    /** 字典项编码 */
    private String dictCode;

    /** 字典标签（用于前端显示） */
    private String dictLabel;

    /** 字典值（用于存储/传输） */
    private String dictValue;

    /** 排序号 */
    private Integer sortOrder;
}
```

### 9.2 CalendarDayDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 日历天传输对象
 */
@Data
public class CalendarDayDTO {

    /** 日期 */
    private LocalDate day;

    /** 是否工作日 */
    private Boolean isWorkday;

    /** 备注 */
    private String remark;
}
```

### 9.3 JobConfDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 任务配置传输对象
 */
@Data
public class JobConfDTO {

    /** 任务ID */
    private String id;

    /** 任务唯一标识 */
    private String jobKey;

    /** 任务名称 */
    private String jobName;

    /** Cron 表达式 */
    private String cronExpr;

    /** 状态 ACTIVE/PAUSED */
    private String status;

    /** 是否允许手动触发 */
    private Boolean allowManualTrigger;

    /** 上次执行时间（ISO 8601） */
    private String lastRunTime;

    /** 下次执行时间（ISO 8601） */
    private String nextRunTime;

    /** 备注 */
    private String remark;
}
```

### 9.4 JobRunLogDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 任务执行日志传输对象
 */
@Data
public class JobRunLogDTO {

    /** 日志ID */
    private String id;

    /** 任务ID */
    private String jobId;

    /** 触发类型 SCHEDULED/MANUAL */
    private String triggerType;

    /** 触发原因（手动触发时） */
    private String reason;

    /** 开始时间（ISO 8601） */
    private String startTime;

    /** 结束时间（ISO 8601） */
    private String endTime;

    /** 运行状态 RUNNING/SUCCESS/FAILED */
    private String status;

    /**
     * 处理状态（来自 PERF_METRIC_CALC_TASK.status，左连接取值；关联不到时为 null）。
     * 订正（2026-07-19）：本文历史版本未收录该字段。
     */
    private String processStatus;

    /** 错误信息（左连接 PERF_METRIC_CALC_TASK.error_msg，关联不到时为 null） */
    private String errorMsg;

    /** 触发人工号 */
    private String createdBy;

    /**
     * 触发人姓名（按工号 createdBy 解析；解析失败为 null）。
     * 订正（2026-07-19）：本文历史版本未收录该字段。
     */
    private String operatorName;
}
```

### 9.5 AuditLogDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 审计日志传输对象
 */
@Data
public class AuditLogDTO {

    /** 日志ID */
    private String id;

    /** 链路追踪ID */
    private String traceId;

    /** 操作人工号 */
    private String empId;

    /** 操作人姓名 */
    private String empName;

    /** 业务类型 */
    private String bizType;

    /** 业务动作 */
    private String bizAction;

    /** 资源URL */
    private String resourceUrl;

    /** 请求方法 */
    private String requestMethod;

    /** 请求参数（脱敏后） */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseStatus;

    /** 错误信息 */
    private String errorMsg;

    /** IP地址 */
    private String ipAddress;

    /**
     * 订正（2026-07-19）：实际 api/dto/AuditLogDTO 源码类中**不存在** userAgent 字段
     * （底层实体 entity/AuditLog 确有 userAgent/user_agent 列落库，但当前查询侧 DTO 未暴露、
     * AuditLogService.toDTO() 也未映射；写入侧 AuditLogCmd 仍保留该字段，见 8.6）。
     */

    /** 执行耗时(ms) */
    private Integer executionTime;

    /** 操作原因（高危动作必填） */
    private String reason;

    /** 操作时间（ISO 8601） */
    private String createdTime;
}
```

### 9.6 AuditLogCmd

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 审计日志写入命令
 * 用于 AuditApi.log() 方法的入参
 */
@Data
@Builder
public class AuditLogCmd {

    /** 链路追踪ID（必填） */
    private String traceId;

    /** 操作人工号（必填） */
    private String empId;

    /** 操作人姓名 */
    private String empName;

    /** 业务类型（必填，如 SYS_CONFIG、LEAD、CUSTOMER） */
    private String bizType;

    /** 业务动作（必填，如 EXPORT、IMPORT、DELETE、JOB_TRIGGER） */
    private String bizAction;

    /** 资源URL */
    private String resourceUrl;

    /** 请求方法（GET/POST/PUT/DELETE） */
    private String requestMethod;

    /** 请求参数（脱敏后） */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseStatus;

    /** 错误信息 */
    private String errorMsg;

    /** IP地址 */
    private String ipAddress;

    /** 用户代理 */
    private String userAgent;

    /** 执行耗时(ms) */
    private Integer executionTime;

    /** 操作原因（TRANSFER/DELETE/IMPORT/RECALC/CONFIG/JOB_TRIGGER 时必填） */
    private String reason;
}
```

**AuditLogCmd 字段约束：**

| 字段 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| traceId | String | 是 | 来自 MDC，链路追踪ID |
| empId | String | 是 | 操作人工号 |
| empName | String | 否 | 操作人姓名 |
| bizType | String | 是 | 业务类型（BizType 枚举值） |
| bizAction | String | 是 | 业务动作（BizAction 枚举值） |
| resourceUrl | String | 否 | 资源URL |
| requestMethod | String | 否 | 请求方法 |
| requestParams | String | 否 | 请求参数（**必须脱敏**：手机号、身份证、账号、金额） |
| responseStatus | Integer | 否 | HTTP响应状态码 |
| errorMsg | String | 否 | 错误信息 |
| ipAddress | String | 否 | IP地址 |
| userAgent | String | 否 | 用户代理 |
| executionTime | Integer | 否 | 执行耗时(ms) |
| reason | String | 条件必填 | TRANSFER/DELETE/IMPORT/RECALC/CONFIG/JOB_TRIGGER 时必填 |

### 9.7 AuditLogQueryReqDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 审计日志查询请求
 */
@Data
public class AuditLogQueryReqDTO {

    /** 操作人工号（精确匹配） */
    private String empId;

    /** 业务类型（精确匹配） */
    private String bizType;

    /** 业务动作（精确匹配） */
    private String bizAction;

    /** 开始时间（ISO 8601） */
    private String startTime;

    /** 结束时间（ISO 8601） */
    private String endTime;

    /** 模糊搜索关键词 */
    private String keyword;
}
```

### 9.8 NotificationDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 通知传输对象
 */
@Data
public class NotificationDTO {

    /** 通知ID */
    private String id;

    /** 接收人工号 */
    private String empId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型 SYSTEM/WORKFLOW/BUSINESS */
    private String notifyType;

    /** 通知类型显示名称 */
    private String notifyTypeLabel;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务ID */
    private String bizId;

    /** 跳转链接 */
    private String linkUrl;

    /** 是否已读 */
    private Boolean isRead;

    /** 阅读时间（ISO 8601） */
    private String readTime;

    /** 创建时间（ISO 8601） */
    private String createdTime;
}
```

### 9.9 NotificationCmd

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 通知发送命令
 * 用于 NotifyApi.sendNotification() 方法的入参
 */
@Data
@Builder
public class NotificationCmd {

    /** 接收人工号（必填） */
    private String targetEmpId;

    /** 通知标题（必填） */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型 SYSTEM/WORKFLOW/BUSINESS */
    private String notifyType;

    /** 关联业务类型（用于跳转） */
    private String bizType;

    /** 关联业务ID（用于跳转） */
    private String bizId;

    /** 跳转链接 */
    private String linkUrl;
}
```

**NotificationCmd 字段约束：**

| 字段 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| targetEmpId | String | 是 | 接收人工号（PT_USER.USER_ID） |
| title | String | 是 | 通知标题（最大 200 字符） |
| content | String | 否 | 通知内容 |
| notifyType | String | 否 | 通知类型，默认 SYSTEM |
| bizType | String | 否 | 关联业务类型（如 LEAD、LOAN） |
| bizId | String | 否 | 关联业务ID |
| linkUrl | String | 否 | 跳转链接（前端路由路径） |

### 9.10 ConfigDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 系统配置传输对象
 */
@Data
public class ConfigDTO {

    /** 配置ID */
    private String id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型 STRING/JSON/NUMBER/BOOL */
    private String valueType;

    /** 状态 ACTIVE/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间（ISO 8601）。订正（2026-07-19）：本文历史版本未收录该字段。 */
    private String createdTime;

    /** 最后更新时间（ISO 8601）。订正（2026-07-19）：本文历史版本未收录该字段。 */
    private String updatedTime;
}
```

订正：`ConfigDTO` 不含 `updatedBy`（最后更新人）字段，`sys_config_kv` 表本身未向该 DTO 暴露更新人信息。

### 9.11 FileObjectDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 文件对象传输对象
 */
@Data
public class FileObjectDTO {

    /** 文件对象ID */
    private String id;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** MD5 哈希值 */
    private String md5Hash;

    /** 文件用途（来自 biz_file_rel.file_role） */
    private String fileRole;

    /** 上传人工号 */
    private String uploadedBy;

    /** 上传时间（ISO 8601） */
    private String uploadedTime;
}
```

### 9.12 SqlProbeRespDTO

> 订正（2026-07-19）：真实类名为 `SqlProbeRespDTO`，本文历史版本误写为 `SqlProbeResultDTO`（字段本身一致）。

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * SQL探查执行响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlProbeRespDTO {

    /** 列名列表 */
    private List<String> columns;

    /** 结果数据 */
    private List<Map<String, Object>> rows;

    /** 返回行数 */
    private Integer rowCount;

    /** 执行耗时(ms) */
    private Integer executionTime;
}
```

### 9.13 RegisterJobCmd（V1.7 新增，本文历史版本未收录）

`JobApi.registerJob(RegisterJobCmd cmd)` 的入参，承载 sys_job_conf upsert + Quartz JobDetail/CronTrigger 注册所需的全部参数：

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;
import java.util.Map;

/**
 * 注册调度任务命令 (V1.7).
 * 由业务模块（如 performance）通过 JobApi.registerJob 调用。
 */
@Data
public class RegisterJobCmd {

    /** 必填：任务唯一标识，对应 sys_job_conf.job_key */
    private String jobKey;

    /** 必填：任务展示名 */
    private String jobName;

    /** 必填：合法 cron 表达式 */
    private String cronExpr;

    /** 必填：QuartzJobBean 子类全限定名 */
    private String quartzJobClass;

    /** 选填：透传到 JobDataMap */
    private Map<String, String> jobData;

    /** 选填：misfire 策略，默认 "FIRE_ONCE_NOW"（可选 "DO_NOTHING"/"IGNORE_MISFIRE_POLICY"） */
    private String misfirePolicy = "FIRE_ONCE_NOW";

    /** 选填：是否允许 JobController 手动触发，默认 true */
    private boolean allowManualTrigger = true;

    /** 选填：备注 */
    private String remark;
}
```

**RegisterJobCmd 字段约束：**

| 字段 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| jobKey | String | 是 | 任务唯一标识，重复调用视为覆盖 |
| jobName | String | 是 | 任务展示名 |
| cronExpr | String | 是 | 非法 cron 表达式抛 `GOV-50010` |
| quartzJobClass | String | 是 | 必须是 `org.quartz.Job` 的子类全限定名，反射失败抛 `GOV-50011` |
| jobData | Map\<String,String\> | 否 | 透传到 Quartz JobDataMap |
| misfirePolicy | String | 否 | 默认 `FIRE_ONCE_NOW` |
| allowManualTrigger | boolean | 否 | 默认 `true` |
| remark | String | 否 | 备注 |

---

### 9.14 PersonTagDTO（2026-07-20 新增）

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/** 人员标签对外 DTO（跨模块契约，仅暴露标识与名称）。 */
@Data
public class PersonTagDTO {
    /** 标签 ID（PERSON_TAG.TAG_ID）. */
    private Long tagId;
    /** 标签名称（全局唯一）. */
    private String tagName;
}
```

---

## 10. 调用约束汇总

> **2026-07-19 订正**：缓存列原文标注的 "Redis TTL Xx分钟/小时" 已全部更正为本地内存缓存（`MemoryCacheService`/Caffeine，全局固定 `expireAfterWrite=1小时`，见文首回填说明第 5 条）；`JobApi` 三行 `startJobRun`/`completeJobRun`/`failJobRun` 已删除，替换为 `registerJob`/`unregisterJob`/`getJobConf`；`FileApi` 补齐历史版本未收录的方法行；`getDownloadUrl()` 预签名有效期订正为 10 分钟；`deleteFile()` "检查引用计数" 订正为当前实现不做引用计数检查。

| API | 调用方式 | 缓存 | 频率预估 | 特殊说明 |
|:---|:---|:---|:---|:---|
| DictApi.getDictItems() | 同步 | 本地内存缓存(1h) | 高 | 缓存穿透保护 |
| DictApi.getDictLabel() | 同步 | 本地内存缓存(1h) | 高 | 建议批量获取后本地查找 |
| DictApi.batchGetDictItems() | 同步 | 本地内存缓存(1h) | 中 | 减少多次调用开销 |
| DictApi.isValidDictValue() | 同步 | 本地内存缓存(1h) | 低 | 表单校验时使用 |
| CalendarApi.isWorkingDay() | 同步 | 本地内存缓存(1h) | 中 | - |
| CalendarApi.countWorkingDays() | 同步 | 本地内存缓存(1h) | 高 | 红绿灯批量计算 |
| CalendarApi.addWorkingDays() | 同步 | 本地内存缓存(1h) | 低 | 流程发起时 |
| CalendarApi.getWorkingDays() | 同步 | 本地内存缓存(1h) | 低 | 绩效计算时 |
| JobApi.getJobConf() | 同步 | 无 | 低 | 不存在时返回 Optional.empty() |
| JobApi.registerJob() | 同步 | 无 | 低 | V1.7 新增，声明式注册/覆盖，同 jobKey 重复调用为覆盖语义 |
| JobApi.unregisterJob() | 同步 | 无 | 低 | V1.7 新增，物理删除 sys_job_conf 行，幂等 |
| AuditApi.log() | 同步 | 无 | 中 | 独立事务（REQUIRES_NEW） |
| AuditApi.queryLogs() | 同步 | 无 | 低 | 仅管理页面使用 |
| NotifyApi.sendNotification() | 异步 | 无 | 中 | 事务提交后执行 |
| NotifyApi.batchSendNotifications() | 异步 | 无 | 低 | 逐条发送，失败不影响其他 |
| NotifyApi.countUnread() | 同步 | 无 | 中 | 工作台首页 |
| ConfigApi.getConfigValue() | 同步 | 本地内存缓存(1h) | 低 | 建议启动时加载 |
| FileApi.upload(file, uploadedBy) | 同步 | 无 | 中 | MD5 去重，category 默认 GENERAL |
| FileApi.upload(file, uploadedBy, category) | 同步 | 无 | 中 | MD5 去重；本文历史版本未收录 |
| FileApi.upload(bytes, ...) | 同步 | 无 | 低 | 字节直传；本文历史版本未收录 |
| FileApi.getFileContent() | 同步 | 无 | 中 | 供 REST 端点直接读字节流响应；本文历史版本未收录 |
| FileApi.writeFileContent() | 同步流式 | 无 | 中 | OBS 输入流直接写调用方 OutputStream，不聚合完整字节 |
| FileApi.getDownloadUrl() | 同步 | 无 | 中 | 预签名 URL 默认有效期 10 分钟（`obs.presignExpireSeconds`，可配置） |
| FileApi.bindFile() | 同步 | 无 | 低 | 幂等操作 |
| FileApi.unbindFile() | 同步事务 | 无 | 低 | 只解除指定业务关联，不删除文件对象 |
| FileApi.listBizFiles() | 同步 | 无 | 中 | - |
| FileApi.deleteFile() | 同步 | 无 | 低 | 订正：当前实现**不做**引用计数检查，无条件删除 |
| FileApi.getFileName() | 同步 | 无 | 中 | 供 Content-Disposition；本文历史版本未收录 |
| FileApi.getFileSizes() | 同步 | 无 | 低 | 批量查询；本文历史版本未收录 |
| FileApi.getFileNames() | 同步 | 无 | 低 | 批量查询，避免 N+1；本文历史版本未收录 |

---

## 11. 领域事件

governance 模块**不主动发布领域事件**。

| 能力 | 实现方式 | 说明 |
|:---|:---|:---|
| 审计日志 | `AuditApi.log()` 同步写入 | 不走事件总线 |
| 通知发送 | `NotifyApi.sendNotification()` 异步 | 使用 `@TransactionalEventListener`，但这是实现细节而非领域事件 |
| 字典/配置缓存 | 写后主动 `evict()` 本地内存缓存 key（订正：已非 Redis，见文首回填说明第 5 条） | 不发布缓存失效事件 |

调用方如需在通知发送后做额外处理（如统计），应自行管理，不依赖 governance 的事件。
