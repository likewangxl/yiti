# 客户营销中心 — 对外 API 契约

> 版本：v1.0
> 最后更新：2026-04-10
> 模块编码：customer-marketing-center
> 本文约束 `customer-marketing-center` 对外暴露的所有 API 接口、DTO 结构、调用约束、领域事件
> 所有外部模块（business-application-center / performance-engine-center / report-analytics-center / workflow-center）必须通过本文定义的接口访问本模块数据

---

## 0. 契约原则

### 0.1 跨模块访问规则
- **唯一入口**：仅通过 `com.bank.branch.platform.customer.api.*` 包下的接口
- **严禁直连**：禁止直接依赖 `mapper` / `entity` / `service` / `serviceImpl`
- **只读为主**：查询类 API 用 `*QueryApi` 命名；写入类 API 用 `*Api` 命名
- **DTO 传输**：所有返回对象为 `api/dto` 下的 DTO，**禁止暴露 Entity**
- **无副作用查询**：`*QueryApi` 的方法不能有副作用（写操作、状态变更）

### 0.2 版本兼容原则
- **向后兼容**：新增字段必须可空，禁止删除已发布字段
- **废弃标记**：使用 `@Deprecated` + Javadoc 说明迁移路径
- **事件版本**：事件名包含版本号（如 `customer.lead.approved.v1`），升级时新增 `.v2` 并并行一段时间

### 0.3 异常规范
- 所有 API 方法仅抛出 `BizException`（含错误码）
- 查询类 API 方法：
  - 单个对象返回 `Optional<T>`（找不到时返回 `Optional.empty()`，不抛异常）
  - 列表返回空 `List`（不返回 null）
  - 布尔校验返回 `boolean`
- 写操作类 API 方法抛出业务异常，附带错误码

### 0.4 性能与限流
- 高频查询接口必须加缓存（TTL 详见各接口说明）
- 批量查询方法的 `ids` 参数最大长度 500，超限抛 `COMMON-40000`
- 搜索方法的 `limit` 参数最大 50
- 所有 Api 方法调用必须走 AOP 日志 + 耗时统计

---

## 1. CustomerQueryApi（客户查询 API，高频）

### 1.1 接口定义
```java
package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import java.util.List;
import java.util.Optional;

/**
 * 客户查询 API
 *
 * 被调用方：
 * - business-application-center（资产投放/中场支持选择客户）
 * - performance-engine-center（绩效计算读取客户信息）
 * - report-analytics-center（报表只读）
 * - workflow-center（流程回写）
 *
 * 高频调用，部分方法带缓存（见各方法说明）
 */
public interface CustomerQueryApi {

    /**
     * 获取客户详情
     *
     * 缓存：cust:customer:{custId}，TTL 5 分钟
     *
     * @param custId 客户 ID
     * @return 客户 DTO，不存在时返回 Optional.empty()
     */
    Optional<CustomerDTO> getCustomer(String custId);

    /**
     * 批量获取客户信息
     *
     * 限制：custIds 最大长度 500
     * 说明：未命中缓存的客户直接查询数据库
     *
     * @param custIds 客户 ID 列表（最大 500）
     * @return 客户 DTO 列表，不存在的 ID 会被过滤
     * @throws BizException COMMON-40000 参数超限
     */
    List<CustomerDTO> listCustomers(List<String> custIds);

    /**
     * 模糊搜索客户
     *
     * 搜索字段：cust_name、cust_no、unified_credit_code
     * 搜索规则：匹配任一字段，按相关度排序
     *
     * @param keyword 关键词（非空）
     * @param limit   最大返回数量（1~50，默认 20）
     * @return 客户列表
     * @throws BizException COMMON-40000 关键词为空或 limit 超限
     */
    List<CustomerDTO> searchCustomers(String keyword, int limit);

    /**
     * 校验客户是否有效
     *
     * 条件：cust_master.status = 'VALID' 且未被逻辑删除
     *
     * @param custId 客户 ID
     * @return true=有效，false=不存在或已删除
     */
    boolean isValidCustomer(String custId);

    /**
     * 查询客户是否被指定机构认领
     *
     * 缓存：cust:customer:{custId}:claims，TTL 3 分钟
     *
     * @param custId  客户 ID
     * @param orgCode 机构编码
     * @return true=该机构有 CLAIMED 状态的 cust_claim
     */
    boolean isClaimedByOrg(String custId, String orgCode);

    /**
     * 查询客户的所有有效认领关系
     *
     * 仅返回 claim_status=CLAIMED 的记录
     *
     * @param custId 客户 ID
     * @return 认领关系列表
     */
    List<CustClaimDTO> getCustomerClaims(String custId);

    /**
     * 查询客户是否存在指定 BizType 的在途流程
     *
     * 调用方：business-application-center 等模块在发起新流程前检查并行流程
     *
     * @param custId  客户 ID
     * @param bizType 业务类型（TOUCH_TASK/LOAN_APPROVAL/MIDFIELD_SUPPORT 等）
     * @return true=存在 PENDING/IN_PROGRESS 流程
     */
    boolean hasRunningProcess(String custId, String bizType);

    /**
     * 查询客户的所有在途流程（全类型）
     *
     * 用于认领前并行流程提示
     *
     * @param custId 客户 ID
     * @return 在途流程列表
     */
    List<RunningFlowDTO> listRunningProcesses(String custId);

    /**
     * 统计客户数量（按过滤条件）
     *
     * 用于工作台卡片、报表
     *
     * @param filter 过滤条件
     * @return 客户数量
     */
    long countCustomers(CustomerFilterDTO filter);
}
```

### 1.2 调用示例
```java
// business-application-center 发起资产投放时
@Autowired
private CustomerQueryApi customerQueryApi;

public void createLoanApplication(LoanApplyReq req) {
    // 1. 校验客户有效性
    if (!customerQueryApi.isValidCustomer(req.getCustId())) {
        throw new BizException(BusinessApplicationErrorCode.CUSTOMER_INVALID);
    }
    // 2. 校验当前机构已认领
    if (!customerQueryApi.isClaimedByOrg(req.getCustId(), currentOrg)) {
        throw new BizException(BusinessApplicationErrorCode.CUSTOMER_NOT_CLAIMED);
    }
    // 3. 获取客户信息
    CustomerDTO customer = customerQueryApi.getCustomer(req.getCustId())
        .orElseThrow(() -> new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    // 4. 继续业务...
}
```

---

## 2. LeadApi（线索查询 API）

### 2.1 接口定义
```java
package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.LeadDTO;
import java.util.List;
import java.util.Optional;

/**
 * 线索查询 API
 *
 * 被调用方：
 * - workflow-center（流程回写业务数据）
 * - report-analytics-center（统计报表）
 */
public interface LeadApi {

    /**
     * 获取线索详情
     *
     * @param leadId 线索 ID
     * @return 线索 DTO
     */
    Optional<LeadDTO> getLead(String leadId);

    /**
     * 按业务键查询线索
     *
     * 业务键格式：
     * - LEAD:{leadId}       单条线索
     * - LEAD:IMP_{batchId}  批量导入
     *
     * @param businessKey 业务键
     * @return 线索 DTO（批量场景返回批次第一条作为样本）
     */
    Optional<LeadDTO> getLeadByBusinessKey(String businessKey);

    /**
     * 查询批次下的线索列表
     *
     * @param importBatchId 批次 ID
     * @return 该批次下所有线索
     */
    List<LeadDTO> getLeadsByBatch(String importBatchId);

    /**
     * 查询线索的完整版本链
     *
     * 返回该客户线索的所有历史版本（按 version_no 升序）
     *
     * @param leadId 任一版本的线索 ID
     * @return 版本链列表
     */
    List<LeadDTO> getLeadVersionChain(String leadId);

    /**
     * 校验线索名称是否可用（全行唯一）
     *
     * 用于前端实时校验
     *
     * @param custName     客户名称
     * @param excludeLeadId 排除的线索 ID（编辑场景）
     * @return true=可用，false=已存在
     */
    boolean isLeadCustNameAvailable(String custName, String excludeLeadId);
}
```

---

## 3. TagApi（标签查询 API）

### 3.1 接口定义
```java
package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.TagDTO;
import java.util.List;

/**
 * 标签查询 API
 *
 * 被调用方：
 * - business-application-center（资产投放展示客户标签）
 * - performance-engine-center（标签作为绩效维度）
 * - report-analytics-center（报表维度）
 */
public interface TagApi {

    /**
     * 获取启用的标签列表
     *
     * 按 tag_priority 升序、tag_name 升序
     * 缓存：cust:tag:enabled:list，TTL 5 分钟
     *
     * @return 启用标签列表
     */
    List<TagDTO> listEnabledTags();

    /**
     * 按 tagCode 获取标签
     *
     * @param tagCode 标签编码
     * @return 标签 DTO
     */
    Optional<TagDTO> getTagByCode(String tagCode);

    /**
     * 获取客户的标签列表
     *
     * 只返回启用状态的标签
     *
     * @param custId 客户 ID
     * @return 标签列表
     */
    List<TagDTO> getCustomerTags(String custId);

    /**
     * 批量获取客户标签
     *
     * @param custIds 客户 ID 列表（最大 500）
     * @return Map<客户 ID, 标签列表>
     */
    Map<String, List<TagDTO>> batchGetCustomerTags(List<String> custIds);

    /**
     * 按标签查询客户 ID 列表
     *
     * 注意：仅返回客户 ID，不做分页，适用于批量处理场景
     *
     * @param tagId 标签 ID
     * @return 客户 ID 列表
     */
    List<String> getCustomerIdsByTag(String tagId);

    /**
     * 校验标签名称是否存在
     *
     * @param tagName 标签名称
     * @return true=已存在
     */
    boolean isTagNameExists(String tagName);
}
```

---

## 4. ClaimApi（认领查询 API）

### 4.1 接口定义
```java
package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import java.util.List;
import java.util.Optional;

/**
 * 认领查询 API
 *
 * 被调用方：
 * - business-application-center（校验客户认领关系）
 * - performance-engine-center（分配关系、绩效计算）
 */
public interface ClaimApi {

    /**
     * 获取客户在指定机构的认领关系
     *
     * @param custId  客户 ID
     * @param orgCode 机构编码
     * @return 认领关系（只返回 CLAIMED 状态的，没有返回 Optional.empty()）
     */
    Optional<CustClaimDTO> getClaim(String custId, String orgCode);

    /**
     * 查询员工已认领的客户列表
     *
     * @param empId 员工 ID
     * @return 认领关系列表（员工为 maintainer_emp_id 且 claim_status=CLAIMED）
     */
    List<CustClaimDTO> getEmpClaims(String empId);

    /**
     * 查询机构已认领的客户列表
     *
     * @param orgCode 机构编码
     * @return 认领关系列表
     */
    List<CustClaimDTO> getOrgClaims(String orgCode);

    /**
     * 校验认领关系是否有效
     *
     * @param custId  客户 ID
     * @param orgCode 机构编码
     * @return true=存在 CLAIMED 状态的认领关系
     */
    boolean isClaimActive(String custId, String orgCode);

    /**
     * 统计员工认领客户数量
     *
     * @param empId 员工 ID
     * @return 已认领客户数量
     */
    long countEmpClaims(String empId);

    /**
     * 统计机构认领客户数量
     *
     * @param orgCode 机构编码
     * @return 已认领客户数量
     */
    long countOrgClaims(String orgCode);
}
```

---

## 5. TouchTaskQueryApi（触达任务查询 API）

### 5.1 接口定义
```java
package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import java.util.List;
import java.util.Optional;

/**
 * 触达任务查询 API
 *
 * 被调用方：
 * - business-application-center（校验是否完成首次触达）
 * - performance-engine-center（触达数据作为绩效输入）
 * - report-analytics-center（报表）
 * - workflow-center（流程回写业务数据）
 */
public interface TouchTaskQueryApi {

    /**
     * 获取触达任务详情
     *
     * @param taskId 任务 ID
     * @return 任务 DTO
     */
    Optional<TouchTaskDTO> getTouchTask(String taskId);

    /**
     * 按业务键查询触达任务
     *
     * @param businessKey 业务键，格式 TOUCH:{taskId}
     * @return 任务 DTO
     */
    Optional<TouchTaskDTO> getTouchTaskByBusinessKey(String businessKey);

    /**
     * 查询员工的触达任务
     *
     * @param empId  员工 ID
     * @param status 状态过滤（可空，空时返回全部状态）
     * @return 任务列表
     */
    List<TouchTaskDTO> getEmpTouchTasks(String empId, String status);

    /**
     * 统计员工进行中触达任务数
     *
     * 用于工作台卡片
     * 缓存：cust:emp:{empId}:touch:running，TTL 1 分钟
     *
     * @param empId 员工 ID
     * @return 数量（PENDING + IN_PROGRESS）
     */
    int countRunningTouchTasks(String empId);

    /**
     * 查询客户的触达历史
     *
     * 按创建时间倒序
     *
     * @param custId 客户 ID
     * @return 触达任务列表（含所有状态）
     */
    List<TouchTaskDTO> getCustomerTouchHistory(String custId);

    /**
     * 查询客户在指定机构的触达历史
     *
     * @param custId  客户 ID
     * @param orgCode 机构编码
     * @return 触达任务列表
     */
    List<TouchTaskDTO> getCustomerTouchHistoryByOrg(String custId, String orgCode);

    /**
     * 校验客户是否已完成首次触达（FIRST_TOUCH SUCCESS）
     *
     * 用于中场支持发起前置校验
     *
     * @param custId  客户 ID
     * @param orgCode 机构编码
     * @return true=已完成
     */
    boolean hasCompletedFirstTouch(String custId, String orgCode);

    /**
     * 统计机构触达汇总
     *
     * @param orgCode   机构编码
     * @param startDate 起始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 汇总 DTO
     */
    TouchTaskSummaryDTO getOrgTouchSummary(String orgCode, String startDate, String endDate);
}
```

---

## 6. DTO 定义

### 6.1 CustomerDTO
```java
package com.bank.branch.platform.customer.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户 DTO
 */
@Data
public class CustomerDTO {
    private String id;                        // 客户 ID
    private String custNo;                    // 客户编号
    private String custName;                  // 客户名称
    private String unifiedCreditCode;         // 统一社会信用代码
    private String industry;                  // 行业代码
    private String industryName;              // 行业名称（字典翻译）
    private String groupType;                 // 集团归属
    private String customerType;              // 客户类型
    private Boolean isKeystone;               // 是否重点客户
    private String enterpriseType;            // 企业性质
    private Boolean isAccountOpened;          // 是否已开户
    private String customerDesc;              // 客户描述
    private BigDecimal creditAmount;          // 授信金额
    private BigDecimal creditExposureAmount;  // 授信敞口
    private String ownerOrgId;                // 来源机构 ID（仅展示用）
    private String ownerOrgName;              // 来源机构名称
    private String leadId;                    // 关联来源线索 ID
    private String status;                    // VALID/DELETED
    private List<String> tagIds;              // 标签 ID 列表
    private LocalDateTime createdAt;          // 创建时间
    private LocalDateTime updatedAt;          // 更新时间
}
```

### 6.2 LeadDTO
```java
@Data
public class LeadDTO {
    private String id;                        // 线索 ID
    private String custName;                  // 客户名称
    private String unifiedCreditCode;         // 统一社会信用代码
    private String industry;                  // 行业
    private String groupType;                 // 集团归属
    private String customerType;              // 客户类型
    private Boolean isKeystone;               // 是否重点
    private String enterpriseType;            // 企业性质
    private Boolean isAccountOpened;          // 是否已开户
    private String customerDesc;              // 客户描述
    private BigDecimal creditAmount;          // 授信金额
    private BigDecimal creditExposureAmount;  // 授信敞口
    private String leadOp;                    // CREATE/UPDATE/DELETE
    private String sourceCustId;              // 来源客户 ID
    private String prevLeadId;                // 上一版本线索 ID
    private Integer versionNo;                // 版本号
    private Boolean isLatest;                 // 是否最新版本
    private String leadStatus;                // DRAFT/PENDING_APPROVAL/APPROVED/REJECTED
    private String businessKey;               // 流程业务键
    private String importBatchId;             // 批次 ID
    private String ownerOrgId;                // 归属机构
    private String createdBy;                 // 创建人
    private LocalDateTime createdAt;          // 创建时间
    private LocalDateTime updatedAt;          // 更新时间
}
```

### 6.3 TagDTO
```java
@Data
public class TagDTO {
    private String id;                        // 标签 ID
    private String tagName;                   // 标签名称
    private String tagCode;                   // 标签编码
    private String tagCategory;               // 标签分类
    private Integer tagPriority;              // 优先级
    private String status;                    // ENABLED/DISABLED
    private String description;               // 描述
}
```

### 6.4 CustClaimDTO
```java
@Data
public class CustClaimDTO {
    private String id;                        // 认领关系 ID
    private String custId;                    // 客户 ID
    private String custName;                  // 客户名称（冗余，避免二次查询）
    private String orgId;                     // 认领机构 ID
    private String orgName;                   // 认领机构名称
    private String maintainerEmpId;           // 维护人 ID
    private String maintainerEmpName;         // 维护人姓名
    private String claimStatus;               // CLAIMED/CANCELLED
    private LocalDateTime claimedAt;          // 认领时间
    private LocalDateTime cancelledAt;        // 取消时间
    private String cancelReason;              // 取消原因
}
```

### 6.5 TouchTaskDTO
```java
@Data
public class TouchTaskDTO {
    private String id;                        // 任务 ID
    private String custId;                    // 客户 ID
    private String custName;                  // 客户名称
    private String orgId;                     // 机构 ID
    private String assigneeEmpId;             // 指派人 ID
    private String assigneeEmpName;           // 指派人姓名
    private String taskType;                  // FIRST_TOUCH/FOLLOW_UP
    private String taskStatus;                // PENDING/IN_PROGRESS/SUCCESS/CANCELLED
    private String businessKey;               // 业务键
    private LocalDateTime slaDeadline;        // SLA 截止时间
    private Boolean slaWarning;               // 是否触发预警
    private LocalDateTime expectedFinishAt;   // 期望完成时间
    private LocalDateTime actualFinishAt;     // 实际完成时间
    private String finishResult;              // SUCCESS/CANCELLED
    private String cancelReason;              // 取消原因
    private Integer logCount;                 // 日志数量
    private LocalDateTime createdAt;          // 创建时间
}
```

### 6.6 TouchLogDTO
```java
@Data
public class TouchLogDTO {
    private String id;                        // 日志 ID
    private String touchTaskId;               // 任务 ID
    private String clientUuid;                // 幂等键
    private String logContent;                // 日志内容
    private List<String> photoUrls;           // 照片 URL 列表
    private String operatorEmpId;             // 操作人
    private String operatorEmpName;           // 操作人姓名
    private String operatorLocation;          // 地理位置
    private LocalDateTime createdAt;          // 创建时间
}
```

### 6.7 RunningFlowDTO
```java
@Data
public class RunningFlowDTO {
    private String bizType;                   // LEAD/TOUCH_TASK/LOAN_APPROVAL/MIDFIELD_SUPPORT
    private String bizId;                     // 业务 ID
    private String businessKey;               // 流程业务键
    private String orgId;                     // 机构 ID
    private String orgName;                   // 机构名称
    private String empId;                     // 发起人 ID
    private String empName;                   // 发起人姓名
    private String status;                    // PENDING/IN_PROGRESS
    private LocalDateTime startedAt;          // 开始时间
}
```

### 6.8 CustomerFilterDTO
```java
@Data
public class CustomerFilterDTO {
    private String keyword;                   // 关键词
    private List<String> industries;          // 行业
    private List<String> customerTypes;       // 客户类型
    private Boolean isKeystone;               // 是否重点
    private String status;                    // VALID/DELETED
    private List<String> orgIds;              // 机构过滤
    private LocalDateTime createdStart;       // 创建时间起
    private LocalDateTime createdEnd;         // 创建时间止
}
```

### 6.9 TouchTaskSummaryDTO
```java
@Data
public class TouchTaskSummaryDTO {
    private String orgId;                     // 机构 ID
    private String orgName;                   // 机构名称
    private Long totalCount;                  // 总任务数
    private Long pendingCount;                // 待开始数
    private Long inProgressCount;             // 进行中数
    private Long successCount;                // 成功数
    private Long cancelledCount;              // 取消数
    private Long slaWarningCount;             // SLA 预警数
    private Double avgDurationHours;          // 平均时长（小时）
}
```

---

## 7. 调用约束

### 7.1 性能约束
| 接口 | 平均 RT 目标 | P95 目标 | QPS 上限 |
|---|---|---|---|
| `CustomerQueryApi.getCustomer` | < 20ms | < 100ms | 1000 |
| `CustomerQueryApi.listCustomers` | < 50ms | < 200ms | 500 |
| `CustomerQueryApi.searchCustomers` | < 100ms | < 500ms | 200 |
| `CustomerQueryApi.isValidCustomer` | < 20ms | < 80ms | 1000 |
| `CustomerQueryApi.isClaimedByOrg` | < 30ms | < 150ms | 500 |
| `TagApi.listEnabledTags` | < 10ms | < 50ms | 2000 |
| `ClaimApi.getEmpClaims` | < 50ms | < 200ms | 500 |
| `TouchTaskQueryApi.countRunningTouchTasks` | < 20ms | < 80ms | 1000 |

### 7.2 缓存策略
| 缓存 Key | TTL | 失效时机 |
|---|---|---|
| `cust:customer:{custId}` | 5 分钟 | 客户更新/删除 |
| `cust:customer:{custId}:claims` | 3 分钟 | 认领变更 |
| `cust:tag:enabled:list` | 5 分钟 | 标签变更 |
| `cust:customer:{custId}:tags` | 3 分钟 | 标签关联变更 |
| `cust:emp:{empId}:touch:running` | 1 分钟 | 触达任务状态变更 |

### 7.3 参数限制
| 参数 | 限制 |
|---|---|
| `custIds` / 批量 ID 列表 | 最大 500 |
| `keyword` | 非空，最大 200 字符 |
| `limit` | 最大 50 |
| `empIds` | 最大 500 |

### 7.4 禁用规则
- **严禁** API 暴露 `Entity` 对象
- **严禁** API 返回数据库原生 `java.sql.*` 类型
- **严禁** 查询 API 包含写操作
- **严禁** 跨模块调用触达写接口（触达任务的写操作只能通过本模块的 Controller）
- **严禁** 外部模块直接操作 `cust_claim`（必须通过本模块的业务流程）

---

## 8. 领域事件

### 8.1 事件规范
所有事件基类：
```java
public abstract class DomainEvent {
    protected String eventId;          // UUID
    protected String eventType;        // 如 customer.lead.approved.v1
    protected String traceId;          // 链路追踪 ID
    protected String aggregateId;      // 聚合根 ID
    protected Long timestamp;          // 事件时间戳
    protected String source;           // 来源模块：customer-marketing-center
    protected Integer version;         // 事件版本号
}
```

### 8.2 事件清单

#### 8.2.1 customer.lead.approved.v1 — 线索审批通过
**发布时机**：线索审批流程结束且结果为通过
**发布方**：`customer-marketing-center` → `workflow-center` 流程回调
**消费方**：
- **自身**：`LeadApprovedListener` → 创建/更新 `cust_master` → 进入待认领池
- `report-analytics-center`：统计维度更新

**事件体**：
```java
public class LeadApprovedEvent extends DomainEvent {
    private String leadId;               // 线索 ID
    private String importBatchId;        // 批次 ID（可空，单条线索为空）
    private String leadOp;               // CREATE/UPDATE/DELETE
    private String custName;             // 客户名称
    private String sourceCustId;         // 来源客户 ID（UPDATE/DELETE 时）
    private String ownerOrgId;           // 归属机构
    private String approverId;           // 审批人 ID
    private LocalDateTime approvedAt;    // 审批通过时间
    // ... 其他字段
}
```

#### 8.2.2 customer.lead.rejected.v1 — 线索审批驳回
**发布时机**：线索审批驳回
**消费方**：自身（更新状态）+ 通知系统

**事件体**：
```java
public class LeadRejectedEvent extends DomainEvent {
    private String leadId;
    private String importBatchId;
    private String approverId;
    private String rejectReason;
    private LocalDateTime rejectedAt;
}
```

#### 8.2.3 customer.claim.created.v1 — 客户被认领
**发布时机**：客户被机构成功认领
**消费方**：
- **自身**：`ClaimCreatedListener` → 创建 `FIRST_TOUCH` 触达任务
- `performance-engine-center`：记录分配关系
- `report-analytics-center`：更新统计

**事件体**：
```java
public class ClaimCreatedEvent extends DomainEvent {
    private String claimId;
    private String custId;
    private String custName;
    private String orgId;
    private String orgName;
    private String maintainerEmpId;
    private String maintainerEmpName;
    private LocalDateTime claimedAt;
}
```

#### 8.2.4 customer.claim.cancelled.v1 — 客户取消认领
**发布时机**：客户取消认领
**消费方**：
- `business-application-center`：清缓存
- `performance-engine-center`：分配关系调整
- `report-analytics-center`：统计更新

**事件体**：
```java
public class ClaimCancelledEvent extends DomainEvent {
    private String claimId;
    private String custId;
    private String orgId;
    private String maintainerEmpId;
    private String cancelReason;
    private String operatorEmpId;
    private LocalDateTime cancelledAt;
}
```

#### 8.2.5 customer.claim.transferred.v1 — 客户转交
**发布时机**：客户转交维护负责人
**消费方**：
- `performance-engine-center`：绩效归属调整
- `report-analytics-center`：统计更新

**事件体**：
```java
public class ClaimTransferredEvent extends DomainEvent {
    private String claimId;
    private String custId;
    private String orgId;
    private String fromEmpId;
    private String toEmpId;
    private String reason;
    private String operatorEmpId;
    private LocalDateTime transferredAt;
}
```

#### 8.2.6 customer.touch.completed.v1 — 触达完成
**发布时机**：触达任务成功或取消
**消费方**：
- `business-application-center`：可能触发中场支持的后续流程
- `performance-engine-center`：触达数据作为绩效输入
- `report-analytics-center`：报表更新
- `portal-content-center`：通知

**事件体**：
```java
public class TouchCompletedEvent extends DomainEvent {
    private String touchTaskId;
    private String custId;
    private String custName;
    private String orgId;
    private String assigneeEmpId;
    private String taskType;             // FIRST_TOUCH/FOLLOW_UP
    private String finishResult;         // SUCCESS/CANCELLED
    private String logContent;           // 日志摘要（前 200 字符）
    private Integer photoCount;          // 照片数量
    private LocalDateTime finishedAt;
}
```

#### 8.2.7 customer.customer.deleted.v1 — 客户删除审批通过
**发布时机**：客户删除审批流程通过
**消费方**：
- `performance-engine-center`：清缓存 + 归档分配关系
- `report-analytics-center`：清缓存 + 归档

**事件体**：
```java
public class CustomerDeletedEvent extends DomainEvent {
    private String custId;
    private String custName;
    private String leadId;               // 触发删除的线索 ID
    private String operatorEmpId;
    private String reason;
    private LocalDateTime deletedAt;
}
```

### 8.3 事件消费约束
- **幂等消费**：所有消费方必须保证幂等（使用 `eventId` 去重）
- **异步处理**：使用 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`
- **失败重试**：消费失败必须记录到失败表，由定时任务重试
- **事件顺序**：不依赖事件顺序（同一聚合根事件可能乱序）
- **版本升级**：发布方新增 `.v2` 事件时保留 `.v1` 至少 2 个版本周期

### 8.4 事件总线实现
- **开发阶段**：Spring `ApplicationEventPublisher`（进程内）
- **生产阶段（未来）**：可升级到 MQ（Kafka/RocketMQ），事件结构保持不变

---

## 9. 向后兼容承诺

### 9.1 稳定性级别
| API | 级别 | 说明 |
|---|---|---|
| `CustomerQueryApi.getCustomer` | STABLE | 字段只增不减 |
| `CustomerQueryApi.listCustomers` | STABLE | 字段只增不减 |
| `CustomerQueryApi.isValidCustomer` | STABLE | |
| `CustomerQueryApi.isClaimedByOrg` | STABLE | |
| `CustomerQueryApi.hasRunningProcess` | STABLE | |
| `LeadApi.*` | STABLE | |
| `TagApi.*` | STABLE | |
| `ClaimApi.*` | STABLE | |
| `TouchTaskQueryApi.*` | STABLE | |
| `CustomerQueryApi.searchCustomers` | EXPERIMENTAL | 搜索算法可能变化 |
| `CustomerQueryApi.listRunningProcesses` | EXPERIMENTAL | 可能拆分为多方法 |

### 9.2 废弃流程
1. 标记 `@Deprecated`，Javadoc 注明迁移路径
2. 发送废弃通知给所有依赖方
3. 保留至少 **2 个版本周期**（建议 6 个月）
4. 删除前再次通知
5. 删除并更新文档

### 9.3 迁移示例
```java
/**
 * @deprecated 自 v2.0 起废弃
 * 请使用 {@link #listCustomers(List)} 替代
 * 计划于 v4.0 删除
 */
@Deprecated
List<CustomerDTO> getCustomers(List<String> ids);
```

---

## 10. 测试与验证

### 10.1 契约测试
- 使用 JUnit 5 + AssertJ
- 测试覆盖率：核心 API 方法 ≥ 90%
- 包括正常路径 + 边界条件 + 异常场景
- 契约测试放在 `*-api-tests` 子模块或 `test/contract/` 目录

### 10.2 Mock 工具
提供 `CustomerQueryApiMock` 供其他模块单元测试使用：
```java
@TestConfiguration
public class CustomerQueryApiMockConfig {
    @Bean
    @Primary
    public CustomerQueryApi customerQueryApi() {
        return Mockito.mock(CustomerQueryApi.class);
    }
}
```

### 10.3 性能验证
- 使用 JMH 做基准测试
- 压力测试使用 JMeter / Gatling
- 监控指标：P50/P95/P99、错误率、QPS

---

## 11. 关联文档索引
| 文档 | 说明 |
|---|---|
| 01-功能规格.md | 业务规则详解 |
| 02-后端架构.md | 模块内部架构 |
| 03-接口设计与报文.md | REST 接口（对应 Controller） |
| docs/common-dev-guide.md | 通用开发规范 |
| auth-permission-center/04-对外API契约.md | 认证授权 API 契约 |
| workflow-center/04-对外API契约.md | 工作流 API 契约 |
