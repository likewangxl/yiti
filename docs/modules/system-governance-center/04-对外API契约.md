# 系统治理中心 -- 对外 API 契约

> 本文档定义 governance 模块提供给其他业务模块调用的 Java 接口契约。
> 所有 Api 接口在模块化单体中为本地方法调用（Spring Bean 注入），无 RPC 开销。
> 所有 Api 方法都是同步强依赖（S），除 NotifyApi.sendNotification() 允许异步（事务提交后执行）。

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
 * 高频调用，内部启用 Redis 缓存（TTL 10分钟）
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
- 所有方法均为同步调用，内部走 Redis 缓存（TTL 10 分钟）
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
 * 高频调用，内部启用 Redis 缓存（TTL 24小时）
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
- 所有方法均为同步调用，内部走 Redis 缓存（TTL 24 小时）
- `isWorkingDay()` 频率预估：红绿灯更新时每条流程调用 1 次
- `countWorkingDays()` 频率预估：红绿灯计算时高频（每 30 分钟批量调用）
- `addWorkingDays()` 频率预估：流程发起时每次调用 1 次
- `getWorkingDays()` 频率预估：低频，绩效计算时使用

---

## 3. JobApi -- 任务调度API

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.JobConfDTO;

import java.util.Optional;

/**
 * 任务调度对外API
 * 供业务模块的定时任务在执行前后上报状态
 */
public interface JobApi {

    /**
     * 获取任务配置
     *
     * @param jobKey 任务唯一标识（如 PERF_DAILY_CALC）
     * @return 任务配置，不存在时返回 Optional.empty()
     */
    Optional<JobConfDTO> getJobConf(String jobKey);

    /**
     * 记录任务执行开始
     * 创建一条 RUNNING 状态的执行日志
     *
     * @param jobId          任务ID
     * @param triggerType    触发类型（SCHEDULED / MANUAL）
     * @param operatorEmpId  触发人工号（SCHEDULED 时传 "SYSTEM"）
     * @return 执行日志ID（后续用于 complete/fail 回调）
     */
    String startJobRun(String jobId, String triggerType, String operatorEmpId);

    /**
     * 记录任务执行结束（成功）
     * 更新执行日志状态为 SUCCESS，设置 end_time
     *
     * @param runLogId 执行日志ID（来自 startJobRun 返回值）
     */
    void completeJobRun(String runLogId);

    /**
     * 记录任务执行结束（失败）
     * 更新执行日志状态为 FAILED，设置 end_time 和 error_msg
     *
     * @param runLogId 执行日志ID（来自 startJobRun 返回值）
     * @param errorMsg 错误信息
     */
    void failJobRun(String runLogId, String errorMsg);
}
```

**调用约束：**
- 调用方（如 performance-engine-center）的定时任务应遵循以下模式：

```java
// 调用方使用示例
@Scheduled(cron = "0 2 * * *")
public void dailyCalc() {
    String runLogId = jobApi.startJobRun(jobId, "SCHEDULED", "SYSTEM");
    try {
        // 执行业务逻辑
        doCalculation();
        jobApi.completeJobRun(runLogId);
    } catch (Exception e) {
        jobApi.failJobRun(runLogId, e.getMessage());
        throw e;
    }
}
```

- `startJobRun()` 会检查是否有 RUNNING 中的日志，若有则抛 `GOV-40901`
- 调用方必须确保 `completeJobRun()` 或 `failJobRun()` 二选一被调用（在 finally 中兜底）

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
 * 高频调用，内部启用 Redis 缓存（TTL 10分钟）
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
     * @throws com.bank.branch.platform.common.web.exception.BizException 配置项不存在时抛 GOV-40401
     * @throws ClassCastException 类型转换失败时
     */
    <T> T getConfigValue(String configKey, Class<T> type);
}
```

**调用约束：**
- 所有方法均为同步调用，内部走 Redis 缓存（TTL 10 分钟）
- 建议在模块启动时通过 `getConfigValue(key, defaultValue)` 加载配置并缓存到本地变量
- 配置变更后 Redis 缓存会被主动清除，下次调用会重新加载
- 频率预估：模块初始化时高频，运行时低频

---

## 7. FileApi -- 文件管理API

```java
package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理对外API
 * 提供统一的文件上传/下载/关联管理能力
 * 文件存储在 MinIO 对象存储中
 */
public interface FileApi {

    /**
     * 上传文件到 MinIO
     * 内部进行格式白名单校验和大小限制校验
     * 支持 MD5 去重（相同文件不重复上传）
     *
     * @param file       文件（MultipartFile）
     * @param uploadedBy 上传人工号
     * @return 文件对象信息
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40903 文件格式不在白名单
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40904 文件大小超限
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50001 MinIO 存储异常
     */
    FileObjectDTO upload(MultipartFile file, String uploadedBy);

    /**
     * 获取文件下载URL（MinIO 预签名 URL，有效期 1 小时）
     *
     * @param fileId 文件对象ID
     * @return 预签名下载 URL
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40404 文件不存在
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
     * 查询业务关联的文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 文件对象列表（按 created_time 升序）
     */
    List<FileObjectDTO> listBizFiles(String bizType, String bizId);

    /**
     * 删除文件
     * 解除所有 biz_file_rel 关联
     * 若无其他引用则从 MinIO 删除实际文件 + 删除 file_object 记录
     *
     * @param fileId 文件对象ID
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40404 文件不存在
     */
    void deleteFile(String fileId);
}
```

**调用约束：**
- `upload()` 为同步操作，文件先上传到 MinIO 再写数据库
- `getDownloadUrl()` 返回的预签名 URL 有效期 1 小时，调用方应在 URL 过期前使用
- `bindFile()` 幂等：相同的 `bizType + bizId + fileObjectId` 不会重复创建关联
- `deleteFile()` 需要注意：如果文件被多个业务对象引用，只解除当前调用方的关联
- `upload()` 限制由 sys_config_kv 控制：
  - `file.upload.allowed.types`：允许的文件格式白名单
  - `file.upload.max.size.mb`：最大文件大小（MB）

---

## 8. DTO 定义

### 8.1 DictItemDTO

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

### 8.2 CalendarDayDTO

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

### 8.3 JobConfDTO

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

### 8.4 JobRunLogDTO

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

    /** 错误信息（失败时） */
    private String errorMsg;

    /** 触发人工号 */
    private String createdBy;
}
```

### 8.5 AuditLogDTO

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

    /** 用户代理 */
    private String userAgent;

    /** 执行耗时(ms) */
    private Integer executionTime;

    /** 操作原因（高危动作必填） */
    private String reason;

    /** 操作时间（ISO 8601） */
    private String createdTime;
}
```

### 8.6 AuditLogCmd

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

### 8.7 AuditLogQueryReqDTO

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

### 8.8 NotificationDTO

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

### 8.9 NotificationCmd

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

### 8.10 ConfigDTO

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
}
```

### 8.11 FileObjectDTO

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

### 8.12 SqlProbeResultDTO

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * SQL 探查结果传输对象
 */
@Data
public class SqlProbeResultDTO {

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

---

## 9. 调用约束汇总

| API | 调用方式 | 缓存 | 频率预估 | 特殊说明 |
|:---|:---|:---|:---|:---|
| DictApi.getDictItems() | 同步 | Redis TTL 10分钟 | 高 | 缓存穿透保护 |
| DictApi.getDictLabel() | 同步 | Redis TTL 10分钟 | 高 | 建议批量获取后本地查找 |
| DictApi.batchGetDictItems() | 同步 | Redis TTL 10分钟 | 中 | 减少多次调用开销 |
| DictApi.isValidDictValue() | 同步 | Redis TTL 10分钟 | 低 | 表单校验时使用 |
| CalendarApi.isWorkingDay() | 同步 | Redis TTL 24小时 | 中 | - |
| CalendarApi.countWorkingDays() | 同步 | Redis TTL 24小时 | 高 | 红绿灯批量计算 |
| CalendarApi.addWorkingDays() | 同步 | Redis TTL 24小时 | 低 | 流程发起时 |
| CalendarApi.getWorkingDays() | 同步 | Redis TTL 24小时 | 低 | 绩效计算时 |
| JobApi.startJobRun() | 同步 | 无 | 低 | 检查并发状态 |
| JobApi.completeJobRun() | 同步 | 无 | 低 | - |
| JobApi.failJobRun() | 同步 | 无 | 低 | - |
| AuditApi.log() | 同步 | 无 | 中 | 独立事务（REQUIRES_NEW） |
| AuditApi.queryLogs() | 同步 | 无 | 低 | 仅管理页面使用 |
| NotifyApi.sendNotification() | 异步 | 无 | 中 | 事务提交后执行 |
| NotifyApi.batchSendNotifications() | 异步 | 无 | 低 | 逐条发送，失败不影响其他 |
| NotifyApi.countUnread() | 同步 | 无 | 中 | 工作台首页 |
| ConfigApi.getConfigValue() | 同步 | Redis TTL 10分钟 | 低 | 建议启动时加载 |
| FileApi.upload() | 同步 | 无 | 中 | MD5 去重 |
| FileApi.getDownloadUrl() | 同步 | 无 | 中 | 预签名 URL 有效期 1 小时 |
| FileApi.bindFile() | 同步 | 无 | 低 | 幂等操作 |
| FileApi.listBizFiles() | 同步 | 无 | 中 | - |
| FileApi.deleteFile() | 同步 | 无 | 低 | 检查引用计数 |

---

## 10. 领域事件

governance 模块**不主动发布领域事件**。

| 能力 | 实现方式 | 说明 |
|:---|:---|:---|
| 审计日志 | `AuditApi.log()` 同步写入 | 不走事件总线 |
| 通知发送 | `NotifyApi.sendNotification()` 异步 | 使用 `@TransactionalEventListener`，但这是实现细节而非领域事件 |
| 字典/配置缓存 | 写后主动清除 Redis KEY | 不发布缓存失效事件 |

调用方如需在通知发送后做额外处理（如统计），应自行管理，不依赖 governance 的事件。
