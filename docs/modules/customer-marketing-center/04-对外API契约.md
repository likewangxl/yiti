# 客户营销中心 — 对外 API 契约

> 版本：v1.1
> 最后更新：2026-07-19
> 模块编码：customer-marketing-center
> 本文约束 `customer-marketing-center` 对外暴露的所有 API 接口、DTO 结构、调用约束、领域事件
> 所有外部模块（business-application-center / performance-engine-center / report-analytics-center / workflow-center）必须通过本文定义的接口访问本模块数据
>
> **2026-07-19 回填说明**：本次依据源码对 `api/` 包与 `event/` 包（注意：事件类实际包路径是
> `com.bank.branch.platform.customer.event`，并非 `api/event`）做全量核对。`CustomerQueryApi` /
> `LeadApi` / `TagApi` / `ClaimApi` / `TouchTaskQueryApi` 五个接口定义与源码基本一致，仅补齐
> §1.1 遗漏的 `getCustomerByCustNo` 方法；§8 领域事件按 V1.11.1（2026-05-01，方向 C 修复）核实重写——
> `LeadApprovedEvent`/`ClaimCreatedEvent` 已删除，事件驱动改为同步方法调用，详见 §8.2.1/§8.2.3 订正说明；
> 现存 5 个事件类的字段列表已按真实源码逐一订正（原文档字段多为设计态超集，与实际 `@Data` POJO 不符）。

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
     * 按客户编号（cust_no）获取客户详情
     *
     * 2026-07-19 回填：源码已有此方法，此前文档遗漏未收录。
     * 区别于 {@link #getCustomer(String)} 的内部 ID 主键查询，供上游模块按业务编号查询客户时使用。
     *
     * @param custNo 客户编号（cust_master.cust_no 列）
     * @return 客户 DTO，不存在时返回 Optional.empty()
     */
    Optional<CustomerDTO> getCustomerByCustNo(String custNo);

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

> **2026-07-19 全量订正**：本节原文档描述的是设计态事件体系（统一 `DomainEvent` 基类 + `eventId`/
> `traceId`/`version` 元数据 + `.v1` 版本化事件名）。**实际源码不是这样实现的**：
> `com.bank.branch.platform.customer.event` 包（不是 `api/event`）下的事件类均为独立的
> `@Data @AllArgsConstructor @NoArgsConstructor` 普通 POJO，**不继承任何公共基类**，没有
> `eventId`/`traceId`/`aggregateId`/`timestamp`/`source`/`version` 字段，事件类名本身就是标识
> （不使用 `customer.xxx.v1` 这种字符串事件名，Spring `ApplicationEventPublisher` 按事件的 Java 类型分发）。
> 以下 §8.1/§8.2 已按源码重写；§8.3/§8.4 描述的幂等/异步/版本化约束当前**均未落地**，标注为已知技术债，
> 不代表现状。

### 8.1 事件类形态（实际）
```java
package com.bank.branch.platform.customer.event;

// 无公共基类，每个事件独立定义，示例（TouchCompletedEvent）：
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TouchCompletedEvent {
    private String taskId;
    private String taskNo;
    private String custId;
    private String assigneeEmpId;
    private String taskType;
}
```
发布方式：`ApplicationEventPublisher.publishEvent(event)`，消费方用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 监听具体 Java 类型。

### 8.2 事件清单（源码现状，2026-07-19 核实）

#### 8.2.1 ~~customer.lead.approved.v1~~ — 已删除（V1.11.1，2026-05-01）
**订正**：`LeadApprovedEvent` 类及其监听器 `LeadApprovedListener` 已在 V1.11.1（方向 C 修复，见
`customer-marketing-center/CLAUDE.md`「线索审批回调」一节）**删除**，不再以事件形式存在。
根因：`@TransactionalEventListener(AFTER_COMMIT)` 嵌套 `@Transactional(REQUIRES_NEW)` 子链路下，
INSERT 显示 commit 成功但实际未持久化（详见 `docs/superpowers/sessions/2026-05-01-v1.11-1-d0-isolation-diagnosis.md`）。

**现状**：`WorkflowCallbackListener` 监听 workflow-center 发布的
`com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent`
（`@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`），
`outcome=APPROVED` 时**直接同步方法调用** `LeadCallbackReconcileService.reconcileApproved(...)`，
其内部再同步调用 `CustMasterAssemblerService.assembleFromLead(...)` 完成 `cust_master` 装配
（CREATE/UPDATE/DELETE 按 `leadOp` 分支），**不再发布任何下游事件**。
`LeadCallbackCompensationService`（Quartz 补偿扫描，`job_key=LEAD_CALLBACK_COMPENSATE`）复用同一套
`reconcileApproved` 逻辑作为兜底。

#### 8.2.2 LeadRejectedEvent — 线索审批驳回
**发布时机**：`WorkflowCallbackListener` 收到 `outcome=REJECTED` 的 `ProcessCompletedEvent`，委托
`LeadCallbackReconcileService.reconcileRejected(...)` 仅推进线索状态（不做 `cust_master` 装配），
随后发布本事件。
**消费方**：**当前 V1 无任何监听器消费**（源码内 grep 确认），保留扩展点（如驳回通知、驳回审计）。

**事件体（真实字段，`event/LeadRejectedEvent.java`）**：
```java
public class LeadRejectedEvent {
    private String leadId;
    private String leadNo;
    private String leadOp;        // CREATE/UPDATE/DELETE
    private String sourceCustId;  // UPDATE/DELETE 时有值
    private String ownerOrgId;
    private String rejectReason;  // 来自 ProcessCompletedEvent.reason，可为 null
    private String operatorEmpId;
}
```
**订正**：原文档字段 `importBatchId`/`approverId`/`rejectedAt` 均不存在；真实字段是
`leadNo`/`leadOp`/`sourceCustId`/`ownerOrgId`/`operatorEmpId`。

#### 8.2.3 ~~customer.claim.created.v1~~ — 已删除（V1.11.1，2026-05-01）
**订正**：`ClaimCreatedEvent` 类及其监听器 `ClaimCreatedListener` 已删除，原因与 8.2.1 相同
（AFTER_COMMIT 嵌套事件时序坑）。
**现状**：`ClaimService.claim()` 认领成功后**同步方法调用** `TouchTaskService.createFromClaim(...)`
创建 `FIRST_TOUCH` 首次触达任务，不再发布事件。

#### 8.2.4 ClaimCancelledEvent — 客户取消认领
**发布时机**：`ClaimService.cancelClaim()` 取消认领成功后。
**消费方**：模块内**当前无监听器**消费（跨模块 business-application-center/performance-engine-center/
report-analytics-center 是否消费需以各自模块源码为准，本模块无法验证）。

**事件体（真实字段，`event/ClaimCancelledEvent.java`）**：
```java
public class ClaimCancelledEvent {
    private String claimId;
    private String custId;
    private String orgId;
    private String cancelReason;
    private String operatorEmpId;
}
```
**订正**：原文档的 `maintainerEmpId`/`cancelledAt` 字段不存在。

#### 8.2.5 ClaimTransferredEvent — 客户转交
**发布时机**：`CustomerService.transfer()` 转交维护人成功后。
**消费方**：模块内当前无监听器消费。

**事件体（真实字段，`event/ClaimTransferredEvent.java`）**：
```java
public class ClaimTransferredEvent {
    private String claimId;
    private String custId;
    private String fromEmpId;
    private String toEmpId;
    private String operatorEmpId;
}
```
**订正**：原文档的 `orgId`/`reason`/`transferredAt` 字段不存在——注意转交原因（`reason`，高危操作必填）
**未被携带进事件体**，仅落在 `@AuditLog` 审计记录里。

#### 8.2.6 TouchCompletedEvent — 触达任务完成
**发布时机**：**仅** `TouchTaskService.markSuccess()` 标记成功后发布。
**订正（重要）**：`TouchTaskService.cancel()`（取消触达）**不发布此事件**，原文档「发布时机：触达任务
成功或取消」不准确，取消场景没有任何事件发布。
**消费方**：模块内 `TouchTaskCompletedListener`（`@TransactionalEventListener(AFTER_COMMIT)`），
**当前实现仅记录日志**，未创建后续跟进任务或更新统计（源码注释明确标注为预留扩展点）。
跨模块（business-application-center/performance-engine-center/report-analytics-center/
portal-content-center）是否消费需以各自模块源码为准。

**事件体（真实字段，`event/TouchCompletedEvent.java`）**：
```java
public class TouchCompletedEvent {
    private String taskId;
    private String taskNo;
    private String custId;
    private String assigneeEmpId;
    private String taskType;      // FIRST_TOUCH/FOLLOW_UP
}
```
**订正**：原文档字段 `touchTaskId`（应为 `taskId`）/`custName`/`orgId`/`finishResult`/`logContent`/
`photoCount`/`finishedAt` 均不存在，字段少得多。

#### 8.2.7 CustomerDeletedEvent — 客户删除
**发布时机**：`CustMasterAssemblerService` 处理线索 `leadOp=DELETE` 审批通过、完成 `cust_master`
逻辑删除（`status=INACTIVE`、`deleted=1`）后发布，**不是**由 workflow 直接触发。
**消费方**：模块内当前无监听器消费。

**事件体（真实字段，`event/CustomerDeletedEvent.java`）**：
```java
public class CustomerDeletedEvent {
    private String custId;
    private String custNo;
    private String operatorEmpId;
}
```
**订正**：原文档字段 `custName`/`leadId`/`reason`/`deletedAt` 均不存在。

### 8.3 事件消费约束（设计态，尚未落地——已知技术债）
以下为原文档设定的目标规范，**当前源码均未实现**，仅保留作为后续演进方向参考：
- **幂等消费**：无 `eventId`，当前无法做去重；如需幂等消费需消费方自行基于业务字段（如 `claimId`/`taskId`）实现。
- **异步处理**：仅用 `@TransactionalEventListener(AFTER_COMMIT)`，**未叠加 `@Async`**（进程内同步分发监听器，只是在事务提交后触发）。
- **失败重试**：无失败记录表、无定时任务重试机制。
- **事件顺序**：不依赖事件顺序，此点符合实际。
- **版本升级**：事件类名无版本号后缀，新增字段需自行评估兼容性。

### 8.4 事件总线实现
- **当前实现**：Spring `ApplicationEventPublisher`（进程内，同步注册 + `AFTER_COMMIT` 阶段触发），与原文档描述一致。
- **生产阶段（未来）**：可升级到 MQ（Kafka/RocketMQ），但需先补齐 §8.3 列出的幂等/重试机制。

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
