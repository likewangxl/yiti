# 业务申请中心 — 对外 API 契约

> 模块代码：`business-application-center`
> Api 子模块名称：`business-application-center-api`
> 基础包：`com.bank.branch.platform.bizapp.api`
> 文档版本：V1.0
>
> **2026-08-28 运行切换**：`LoanApi`、`LoanQueryApi` 及其实现已从本模块删除。资产立项跨模块只读能力
> 由 `customer-marketing-center` 的 `AssetProjectQueryApi` 提供。本文 Loan 章节仅保留为迁移前历史记录，
> 不得继续作为编译或调用依据；本模块对外仅保留 Support 与综合统计契约。

---

## 契约总则

本模块对外暴露的所有能力必须满足：

1. **只依赖 Api 接口**：其他模块通过 `business-application-center-api` 子 artifact 依赖本模块
2. **禁止直接依赖 mapper/entity**：这是平台级红线
3. **同步调用**：所有 Api 为同步调用（`Optional<T>` / `List<T>` / 基础类型）
4. **轻量 DTO**：Api DTO 只含必要字段，不含审批日志等重量级数据
5. **无事务传播**：Api 调用不开启事务
6. **幂等与只读**：所有 Query 方法必须是只读幂等的
7. **错误码一致**：Api 内部抛出 `BizException` 时使用 `BIZ-*` 错误码，不吞异常

---

## 1. LoanApi（资产投放查询 API）

### 接口定义

```java
package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import java.util.List;
import java.util.Optional;

/**
 * 资产投放申请对外 API
 *
 * 提供资产投放申请的基础查询能力，用于其他模块（如绩效、报表、门户）获取业务数据。
 * 所有方法只读，不改变本模块状态。
 */
public interface LoanApi {

    /**
     * 按申请 ID 查询资产投放申请。
     *
     * @param applyId 申请 ID
     * @return Optional 包装，若申请不存在或已软删除返回 empty
     */
    Optional<LoanApplyDTO> getLoanApply(String applyId);

    /**
     * 按业务键查询资产投放申请。
     *
     * 业务键格式：{@code LOAN:{applyId}}
     * 主要用于 workflow-center 通过业务键反查业务数据。
     *
     * @param businessKey 业务键
     * @return Optional 包装
     */
    Optional<LoanApplyDTO> getLoanApplyByBusinessKey(String businessKey);

    /**
     * 查询指定客户的历史资产投放申请（按创建时间倒序）。
     *
     * 用于：
     *  - customer-marketing-center：客户 360 页面展示历史业务
     *  - performance-engine-center：按客户维度统计历史授信
     *  - report-analytics-center：只读分析
     *
     * @param custId 客户 ID
     * @return 历史申请列表，不含审批日志
     */
    List<LoanApplyDTO> getCustomerLoanHistory(String custId);

    /**
     * 批量按申请 ID 查询。
     *
     * 主要用于绩效和报表批量加载。
     *
     * @param applyIds 申请 ID 列表，最大 500
     * @return 命中的申请列表
     */
    List<LoanApplyDTO> getLoanApplyBatch(List<String> applyIds);
}
```

### 2. LoanQueryApi（资产投放聚合查询 API）

```java
package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.api.dto.LoanQueryConditionDTO;
import com.bank.branch.platform.common.web.PageResult;

/**
 * 资产投放申请聚合查询 API
 *
 * 用于 report-analytics-center 等只读模块的分页查询。
 * 注意：此 API 不处理权限，调用方需自行做权限收敛。
 */
public interface LoanQueryApi {

    /**
     * 分页查询资产投放申请（不含权限过滤）。
     *
     * @param condition 查询条件（含 orgId、status、timeRange 等）
     * @return 分页结果
     */
    PageResult<LoanApplyDTO> pageQuery(LoanQueryConditionDTO condition);

    /**
     * 统计指定时间段内指定机构的已完成申请数。
     *
     * @param orgId 机构 ID（支持子树查询）
     * @param startTime 起始时间（含）
     * @param endTime 截止时间（不含）
     * @return 已完成申请数
     */
    long countCompletedByOrg(String orgId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

    /**
     * 按员工汇总指定时间段的资产投放金额。
     *
     * @param empId 员工 ID
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return 汇总金额（单位万元）
     */
    java.math.BigDecimal sumCreditAmountByEmp(String empId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);
}
```

---

## 3. SupportApi（中场支持查询 API）

```java
package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import java.util.List;
import java.util.Optional;

/**
 * 中场支持申请对外 API
 *
 * 提供中场支持申请的基础查询能力。
 */
public interface SupportApi {

    /**
     * 按申请 ID 查询中场支持申请。
     */
    Optional<SupportRequestDTO> getSupportRequest(String requestId);

    /**
     * 按业务键查询中场支持申请。
     *
     * 业务键格式：{@code SUPPORT:{requestId}}
     */
    Optional<SupportRequestDTO> getSupportRequestByBusinessKey(String businessKey);

    /**
     * 查询指定客户的中场支持申请历史。
     *
     * @param custId 客户 ID
     * @return 历史申请列表，不含审批日志
     */
    List<SupportRequestDTO> getCustomerSupportHistory(String custId);

    /**
     * 查询同批拆单的所有申请。
     *
     * 当客户经理一次性选择多个产品提交时，系统会按 productId 拆单，
     * 同一批次的所有记录共享一个 submit_group_id。此接口用于查询同批。
     *
     * @param submitGroupId 同批分组 ID
     * @return 同批申请列表
     */
    List<SupportRequestDTO> getBySubmitGroup(String submitGroupId);

    /**
     * 批量按申请 ID 查询。
     *
     * @param requestIds 申请 ID 列表，最大 500
     * @return 命中的申请列表
     */
    List<SupportRequestDTO> getSupportRequestBatch(List<String> requestIds);
}
```

### 4. SupportQueryApi（中场支持聚合查询 API）

```java
package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportQueryConditionDTO;
import com.bank.branch.platform.common.web.PageResult;

public interface SupportQueryApi {

    /**
     * 分页查询中场支持申请（不含权限过滤）。
     */
    PageResult<SupportRequestDTO> pageQuery(SupportQueryConditionDTO condition);

    /**
     * 统计员工作为发起人的已完成中场支持数量。
     */
    long countCompletedByCreator(String empId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

    /**
     * 统计员工作为承接人的已完成中场支持数量。
     */
    long countCompletedByAssignee(String empId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);
}
```

---

## 5. BizApplyQueryApi（跨业务通用查询 API）

```java
package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.api.dto.RunningAppCountDTO;

/**
 * 跨业务申请通用查询 API
 *
 * 按客户、员工等维度聚合 LOAN 和 SUPPORT 两类申请，供门户、绩效、前端并行校验使用。
 */
public interface BizApplyQueryApi {

    /**
     * 统计指定客户的在途业务申请数（含 LOAN 和 SUPPORT）。
     *
     * "在途"定义：status in (IN_APPROVAL, IN_PROGRESS)
     *
     * @param custId 客户 ID
     * @return 在途申请数量明细
     */
    RunningAppCountDTO countRunningApplications(String custId);

    /**
     * 查询客户是否存在进行中的资产投放申请。
     */
    boolean hasRunningLoan(String custId);

    /**
     * 查询客户是否存在进行中的中场支持申请。
     */
    boolean hasRunningSupport(String custId);

    /**
     * 查询员工发起的业务申请统计（全量，不分时间）。
     *
     * 用于工作台数据卡片展示。
     *
     * @param empId 员工 ID
     * @return 统计汇总
     */
    BizApplyStatDTO getEmpStatistics(String empId);

    /**
     * 查询员工在指定时间范围内发起的业务申请统计。
     *
     * @param empId 员工 ID
     * @param startTime 起始时间（含）
     * @param endTime 截止时间（不含）
     * @return 统计汇总
     */
    BizApplyStatDTO getEmpStatisticsByPeriod(
        String empId,
        java.time.LocalDateTime startTime,
        java.time.LocalDateTime endTime);
}
```

---

## 6. DTO 定义

### 6.1 LoanApplyDTO

```java
package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LoanApplyDTO {
    /** 申请 ID */
    private String id;
    /** 申请编号（面向用户的单号） */
    private String applyNo;
    /** 客户 ID */
    private String custId;
    /** 客户名称（冗余，仅展示，以 CustomerQueryApi 返回为准） */
    private String custName;
    /** 来源触达任务 ID（可空） */
    private String sourceTouchTaskId;
    /** 项目类型（字典 PROJECT_TYPE） */
    private String projectType;
    /** 业务类型（字典 BIZ_TYPE） */
    private String bizType;
    /** 主要担保方式（字典 GUARANTEE_TYPE） */
    private String guaranteeType;
    /** 授信金额（万元） */
    private BigDecimal creditAmount;
    /** 授信敞口金额（万元） */
    private BigDecimal creditExposureAmount;
    /** 状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED */
    private String status;
    /** 流程业务键 LOAN:{id} */
    private String businessKey;
    /** 流程实例 ID（提交后才有） */
    private String processInstanceId;
    /** 归属机构（发起人所在经营机构） */
    private String ownerOrgId;
    /** 发起人员工 ID */
    private String createdBy;
    /** 发起时间 */
    private LocalDateTime createdTime;
    /** 最近更新人 */
    private String updatedBy;
    /** 最近更新时间 */
    private LocalDateTime updatedTime;
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | 申请 ID |
| applyNo | String | 申请编号 |
| custId | String | 客户 ID |
| custName | String | 客户名称（冗余） |
| sourceTouchTaskId | String | 来源触达任务 ID（可空） |
| projectType | String | 项目类型 |
| bizType | String | 业务类型 |
| guaranteeType | String | 担保方式 |
| creditAmount | BigDecimal | 授信金额（万元） |
| creditExposureAmount | BigDecimal | 敞口金额（万元） |
| status | String | 状态 |
| businessKey | String | 流程业务键 |
| processInstanceId | String | 流程实例 ID |
| ownerOrgId | String | 归属机构 |
| createdBy | String | 发起人 |
| createdTime | LocalDateTime | 发起时间 |

### 6.2 SupportRequestDTO

```java
package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SupportRequestDTO {
    /** 申请 ID */
    private String id;
    /** 申请编号 */
    private String requestNo;
    /** 同批拆单分组 ID */
    private String submitGroupId;
    /** 来源类型 TOUCH_TASK / EXISTING_CUSTOMER */
    private String sourceType;
    /** 来源触达任务 ID（可空） */
    private String sourceTouchTaskId;
    /** 客户 ID */
    private String custId;
    /** 客户名称（冗余） */
    private String custName;
    /** 产品 ID（拆单后单个产品） */
    private String productId;
    /** 产品名称（冗余） */
    private String productName;
    /** 承接部门 ID（场景 B 必填） */
    private String supportDeptId;
    /** 承接部门名称（冗余） */
    private String supportDeptName;
    /** 其他需求文本（场景 B） */
    private String otherDemand;
    /** 场景 A / B */
    private String scenario;
    /** 派单人员工 ID（秘书） */
    private String dispatchEmpId;
    /** 派单时间 */
    private LocalDateTime dispatchTime;
    /** 承接人员工 ID */
    private String assignedEmpId;
    /** 状态 DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED */
    private String status;
    /** 业务键 SUPPORT:{id} */
    private String businessKey;
    /** 流程实例 ID */
    private String processInstanceId;
    /** 归属机构 */
    private String ownerOrgId;
    /** 发起人 */
    private String createdBy;
    /** 发起时间 */
    private LocalDateTime createdTime;
    /** 最近更新人 */
    private String updatedBy;
    /** 最近更新时间 */
    private LocalDateTime updatedTime;
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | 申请 ID |
| requestNo | String | 申请编号 |
| submitGroupId | String | 同批拆单分组 ID |
| sourceType | String | 来源类型 |
| sourceTouchTaskId | String | 来源触达任务 ID |
| custId | String | 客户 ID |
| productId | String | 产品 ID（单个） |
| supportDeptId | String | 承接部门 |
| otherDemand | String | 其他需求 |
| scenario | String | A/B 场景 |
| dispatchEmpId | String | 派单人 |
| assignedEmpId | String | 承接人 |
| status | String | 状态 |
| businessKey | String | 业务键 |

### 6.3 BizApplyStatDTO

```java
package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BizApplyStatDTO {
    /** 员工 ID */
    private String empId;
    /** 发起的资产投放总数 */
    private long loanTotal;
    /** 已完成的资产投放数 */
    private long loanCompleted;
    /** 在途的资产投放数 */
    private long loanRunning;
    /** 资产投放总授信金额（已完成部分） */
    private BigDecimal loanCompletedAmount;
    /** 发起的中场支持总数 */
    private long supportTotal;
    /** 已完成的中场支持数 */
    private long supportCompleted;
    /** 在途的中场支持数 */
    private long supportRunning;
    /** 作为承接人已完成的中场支持数 */
    private long supportAssignedCompleted;
}
```

### 6.4 RunningAppCountDTO

```java
package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

@Data
public class RunningAppCountDTO {
    /** 客户 ID */
    private String custId;
    /** 在途资产投放数 */
    private int loanRunningCount;
    /** 在途中场支持数 */
    private int supportRunningCount;
    /** 在途申请总数 */
    public int getTotal() {
        return loanRunningCount + supportRunningCount;
    }
}
```

### 6.5 LoanQueryConditionDTO / SupportQueryConditionDTO

```java
@Data
public class LoanQueryConditionDTO {
    private String orgId;
    private List<String> statuses;
    private LocalDateTime createdTimeStart;
    private LocalDateTime createdTimeEnd;
    private String projectType;
    private String bizType;
    private int pageNo = 1;
    private int pageSize = 20;
}

@Data
public class SupportQueryConditionDTO {
    private String orgId;
    private List<String> statuses;
    private String sourceType;
    private String scenario;
    private LocalDateTime createdTimeStart;
    private LocalDateTime createdTimeEnd;
    private int pageNo = 1;
    private int pageSize = 20;
}
```

---

## 7. 调用约束

### 7.1 基本约束

1. **同步调用**：所有 Api 方法为同步阻塞调用，不使用 Future/CompletableFuture
2. **只读原则**：Query 类方法一律只读，不改变本模块任何状态
3. **轻量返回**：`getCustomerLoanHistory` / `getCustomerSupportHistory` 仅返回基础字段，**不含审批日志**、**不含流程图**、**不含附件详情**
4. **跨模块强制走 Api**：禁止其他模块通过 `mapper`/`entity` 直连本模块表
5. **批量接口限制**：`getLoanApplyBatch` / `getSupportRequestBatch` 的入参 ID 列表上限为 500，超限抛 `IllegalArgumentException`
6. **幂等保证**：同参数调用返回相同结果（查询类 Api 本身就是幂等的）

### 7.2 权限说明

**重要**：Api 层 **不做数据范围过滤**，调用方负责自行收敛权限。

- 报表只读模块、绩效模块等 **内部系统调用** 通过 `BizApplyQueryApi` 和 `LoanQueryApi` 获取全量数据
- 面向前端的 REST 接口通过 `LoanController` / `SupportController` 走 `@BizAuth` 注解做权限校验
- 两条路径不共用同一 Service 方法

### 7.3 异常处理

- Api 方法内部抛出 `BizException(BIZ-*)` 表示业务错误
- 抛出 `IllegalArgumentException` 表示入参不合法（如 ID 列表超限）
- 不吞异常，不返回 null（空结果返回 `Optional.empty()` 或空 List）

### 7.4 性能预期

| 方法 | 期望响应时间 |
|---|---|
| `getLoanApply` / `getSupportRequest` | < 20 ms |
| `getCustomerLoanHistory` / `getCustomerSupportHistory` | < 100 ms |
| `getLoanApplyBatch` / `getSupportRequestBatch` | < 200 ms（500 条） |
| `countRunningApplications` | < 50 ms |
| `getEmpStatistics` | < 300 ms |
| `pageQuery` | < 500 ms |

超过预期时间的慢查询必须走告警，由本模块自行优化索引或缓存。

---

## 8. 领域事件

本章节先描述**本模块订阅的上游事件**（§8.0），再描述**本模块对外发布的事件**（§8.1+）。

### 8.0 订阅的上游事件（来自 workflow-center）

本模块是流程发起方也是流程状态的**最终持有者**，通过订阅 workflow-center 发布的流程事件完成状态回写，再对下游发布 `bizapp.*.approved/rejected/completed` 事件。**完整链路**：

```
发起申请 → LoanService.submit / SupportService.submit
         ↓（发 bizapp.loan.submitted.v1 / bizapp.support.submitted.v1）
workflow-center 执行审批 / 办理
         ↓
流程最终节点完成 → workflow-center 发布 workflow.process.completed.v1
         ↓（本模块订阅）
WorkflowCompletedListener.onCompleted
  1. 根据 businessKey 解析 bizType + bizId
  2. SELECT ... FOR UPDATE 锁定主表行
  3. 按 outcome 迁移 status: COMPLETED / REJECTED
  4. 写 updated_time、updated_by
  5. 发布 bizapp.loan.approved.v1 / .rejected.v1 / .support.completed.v1
```

#### 8.0.1 订阅 `workflow.process.completed.v1`

**上游事件来源**：`workflow-center` 流程实例到达最终节点时发布（参考 `docs/modules/workflow-center/04-对外API契约.md §8`）

**本模块处理方式**：`@TransactionalEventListener(AFTER_COMMIT)` 监听，在**独立事务**中更新业务主表

> 实现差异（2026-04-14）：`workflow-center` 当前实际发布的是 `ProcessCompletedListener.ProcessCompletedEvent(processInstanceId, businessKey)`，只有最小载荷。下表是 `business-application-center` 期望的扩展事件模型；在 workflow-center 未补齐扩展 DTO 前，消费方需要依赖 `businessKey`、流程映射查询或业务侧本地状态推断补足信息。

**事件载荷 DTO：`WorkflowProcessCompletedEvent`**

| 字段 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `eventId` | String | 是 | 全局事件 ID |
| `eventType` | String | 是 | 固定 `workflow.process.completed.v1` |
| `eventTime` | LocalDateTime | 是 | 流程完成时刻 |
| `traceId` | String | 是 | 全链路追踪 ID |
| `payload.processInstanceId` | String | 是 | Flowable 流程实例 ID |
| `payload.processDefinitionKey` | String | 是 | 流程定义 Key（`loan_approve_v1` / `support_simple_v1` / `support_complex_v1`） |
| `payload.businessKey` | String | 是 | 业务键，格式 `LOAN:{id}` 或 `SUPPORT:{id}` |
| `payload.outcome` | String | 是 | 流程结果：`APPROVED` / `REJECTED` / `CANCELLED` |
| `payload.finalTaskKey` | String | 是 | 最后一个节点的 task definition key |
| `payload.finalApproverEmpId` | String | 否 | 最后审批人工号（CANCELLED 时可能为空） |
| `payload.finalApproverOrgId` | String | 否 | 最后审批人机构 |
| `payload.finalApprovalTime` | LocalDateTime | 是 | 最终节点完成时间 |
| `payload.reason` | String | 否 | 审批意见 / 驳回原因 / 撤回原因 |
| `payload.variables` | Map\<String, Object\> | 否 | 流程变量快照（含节点表单数据，如 `isNeedCreditMeeting`） |

**载荷示例（审批通过）**：

```json
{
  "eventId": "wf_evt_20260410_001",
  "eventType": "workflow.process.completed.v1",
  "eventTime": "2026-04-10T15:00:00.000+08:00",
  "traceId": "trace-abc-xyz",
  "payload": {
    "processInstanceId": "piid_abc123",
    "processDefinitionKey": "loan_approve_v1",
    "businessKey": "LOAN:LA202604100001",
    "outcome": "APPROVED",
    "finalTaskKey": "credit_approval",
    "finalApproverEmpId": "E99001",
    "finalApproverOrgId": "ORG_HEAD",
    "finalApprovalTime": "2026-04-10T15:00:00.000+08:00",
    "reason": "同意授信",
    "variables": {
      "isNeedCreditMeeting": true,
      "creditMeetingNo": "CM20260410-01",
      "approvedAmount": 5000000.00
    }
  }
}
```

**载荷示例（审批驳回）**：

```json
{
  "eventId": "wf_evt_20260410_002",
  "eventType": "workflow.process.completed.v1",
  "eventTime": "2026-04-10T16:30:00.000+08:00",
  "traceId": "trace-def-xyz",
  "payload": {
    "processInstanceId": "piid_def456",
    "processDefinitionKey": "loan_approve_v1",
    "businessKey": "LOAN:LA202604100002",
    "outcome": "REJECTED",
    "finalTaskKey": "corp_review",
    "finalApproverEmpId": "E88001",
    "finalApproverOrgId": "ORG_CORP",
    "finalApprovalTime": "2026-04-10T16:30:00.000+08:00",
    "reason": "客户信用评级不足",
    "variables": {}
  }
}
```

#### 8.0.2 消费处理契约

**businessKey 解析**：

```java
public record ParsedBusinessKey(String bizType, String bizId) {
    public static ParsedBusinessKey parse(String businessKey) {
        int colonIdx = businessKey.indexOf(':');
        if (colonIdx <= 0) throw new BizException("BIZ-40001", "非法 businessKey: " + businessKey);
        return new ParsedBusinessKey(
            businessKey.substring(0, colonIdx),  // LOAN / SUPPORT
            businessKey.substring(colonIdx + 1)  // LA202604100001 / SR202604100001
        );
    }
}
```

**状态迁移规则**：

| 当前 status | outcome | 目标 status | 发布事件 |
|---|---|---|---|
| `IN_APPROVAL` | `APPROVED` | `COMPLETED`（LOAN）/ `IN_PROGRESS`（SUPPORT 场景B，触发承接）/ `COMPLETED`（其他） | `bizapp.loan.approved.v1` / `bizapp.support.dispatched.v1` / `bizapp.support.completed.v1` |
| `IN_APPROVAL` | `REJECTED` | `REJECTED` | `bizapp.loan.rejected.v1` / `bizapp.support.rejected.v1` |
| `IN_APPROVAL` | `CANCELLED` | `CANCELLED` | 不发事件（用户主动撤回已有 cancel 事件） |
| 其他状态 | 任意 | 保持不变（幂等处理） | 不发事件 |

**幂等要求**：

1. `WorkflowCompletedListener` 按 `eventId` 去重，使用 `sys_event_consumed` 表（governance 提供）
2. 状态迁移使用 **条件 UPDATE**：`UPDATE loan_apply SET status='COMPLETED' WHERE business_key=? AND status='IN_APPROVAL'`
3. 如果 `rowsAffected = 0`，记录 WARN 日志但不抛异常（说明已被其他实例处理）
4. 发布下游事件前再次确认 `rowsAffected > 0`，避免重复发布

**异常处理**：

- 流程变量读取失败：记录 ERROR 日志，状态仍迁移，但 `variables` 字段在 `bizapp.*.approved.v1` 中置为空对象
- 业务主表不存在（被物理删除）：记录 ERROR 日志 + 告警，不抛异常（避免无限重试）
- 条件 UPDATE 乐观锁失败：属幂等场景，直接 return

**超时与重试**：

- 监听器本身同步执行（AFTER_COMMIT），不阻塞 workflow 事务
- 如果本模块事务失败，由消息投递层保证重试（governance 的事件总线负责）
- 最大重试 3 次，超过后写入死信队列 `dead_letter_event`

#### 8.0.3 订阅 `workflow.task.claimed.v1`（仅 SUPPORT 场景 B）

**上游事件来源**：`workflow-center` 任务被认领（`TaskService.claim`）时发布

**本模块处理方式**：仅更新 `support_request.assigned_emp_id`（当前承接人），不迁移 status

**事件载荷 DTO：`WorkflowTaskClaimedEvent`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | String | 全局事件 ID |
| `eventType` | String | 固定 `workflow.task.claimed.v1` |
| `payload.processInstanceId` | String | 流程实例 ID |
| `payload.businessKey` | String | 业务键 |
| `payload.taskId` | String | Flowable Task ID |
| `payload.taskDefinitionKey` | String | 节点 key（仅 `support_staff_handle` 触发本模块更新） |
| `payload.assigneeEmpId` | String | 被认领的员工工号 |
| `payload.claimTime` | LocalDateTime | 认领时间 |

**处理条件**：只有 `processDefinitionKey = support_complex_v1` 且 `taskDefinitionKey = support_staff_handle` 时本模块才处理，其他节点忽略。

#### 8.0.4 事件消费的可观测性

本模块为 workflow 事件消费埋点：

| 指标 | 类型 | 说明 |
|---|---|---|
| `bizapp.event.consumed.count` | Counter | 成功消费事件计数（按 eventType 打标） |
| `bizapp.event.skipped.count` | Counter | 幂等跳过事件计数 |
| `bizapp.event.failed.count` | Counter | 消费失败事件计数（告警） |
| `bizapp.event.consume.duration` | Histogram | 消费耗时（p50/p95/p99） |

告警规则：`bizapp.event.failed.count` 1 分钟内 > 5 次告警。

---

### 8.1 `bizapp.loan.submitted.v1`

**发布时机**：资产投放申请提交成功（`LoanService.submit` 事务成功提交后）

**典型消费方**：`portal-content-center`（工作台待办）

**载荷示例**：

```json
{
  "eventId": "evt_001",
  "eventType": "bizapp.loan.submitted.v1",
  "eventTime": "2026-03-04T10:25:33.000+08:00",
  "traceId": "xxx",
  "payload": {
    "loanId": "loan_001",
    "applyNo": "LN20260304000001",
    "custId": "cust_1001",
    "createdBy": "emp_2001",
    "ownerOrgId": "org_0101",
    "processInstanceId": "piid_abc123",
    "creditAmount": 5000.00
  }
}
```

### 8.2 `bizapp.loan.approved.v1`

**发布时机**：资产投放流程完成（通过） —— 本模块监听到 `workflow.process.completed.v1` 且结果为 `APPROVED` 后，本模块状态迁移到 `COMPLETED` 并发布此事件

**典型消费方**：

- `portal-content-center`：发送通知给发起人
- `report-analytics-center`：汇总统计
- `customer-marketing-center`（弱依赖）：更新客户标签

**载荷**：

```json
{
  "eventId": "evt_002",
  "eventType": "bizapp.loan.approved.v1",
  "eventTime": "2026-03-05T15:00:00.000+08:00",
  "traceId": "yyy",
  "payload": {
    "loanId": "loan_001",
    "applyNo": "LN20260304000001",
    "custId": "cust_1001",
    "createdBy": "emp_2001",
    "creditAmount": 5000.00,
    "creditExposureAmount": 5000.00,
    "completeTime": "2026-03-05T15:00:00.000+08:00"
  }
}
```

### 8.3 `bizapp.loan.rejected.v1`

**发布时机**：资产投放流程驳回（最终结果）

**典型消费方**：`portal-content-center`（通知发起人）

**载荷**：

```json
{
  "eventId": "evt_003",
  "eventType": "bizapp.loan.rejected.v1",
  "eventTime": "2026-03-05T10:00:00.000+08:00",
  "payload": {
    "loanId": "loan_001",
    "applyNo": "LN20260304000001",
    "custId": "cust_1001",
    "createdBy": "emp_2001",
    "rejectNodeKey": "loan_credit_review",
    "rejectReason": "授信金额超出客户上限"
  }
}
```

### 8.4 `bizapp.support.submitted.v1`

**发布时机**：中场支持申请提交成功（每条拆单后的记录独立发一条事件）

**典型消费方**：`portal-content-center`

**载荷**：

```json
{
  "eventId": "evt_004",
  "eventType": "bizapp.support.submitted.v1",
  "eventTime": "2026-03-04T11:00:00.000+08:00",
  "payload": {
    "supportRequestId": "sreq_001",
    "requestNo": "SR20260304000001",
    "submitGroupId": "grp_abc",
    "custId": "cust_1001",
    "productId": "prod_001",
    "scenario": "A",
    "createdBy": "emp_2001"
  }
}
```

### 8.5 `bizapp.support.dispatched.v1`

**发布时机**：秘书派单完成（包括转交动作）

**典型消费方**：`portal-content-center`（通知被指派的承接人）

**载荷**：

```json
{
  "eventId": "evt_005",
  "eventType": "bizapp.support.dispatched.v1",
  "eventTime": "2026-03-04T14:00:00.000+08:00",
  "payload": {
    "supportRequestId": "sreq_010",
    "supportDeptId": "dept_wealth",
    "dispatchEmpId": "emp_sec_001",
    "assignedEmpId": "emp_4001",
    "isTransfer": false,
    "dispatchRemark": "请王五同学承办"
  }
}
```

### 8.6 `bizapp.support.completed.v1`

**发布时机**：中场支持流程完成（结果 = SUCCESS/FAILED/CANCELLED）

**典型消费方**：`portal-content-center`（通知发起人）、`performance-engine-center`（弱依赖）

**载荷**：

```json
{
  "eventId": "evt_006",
  "eventType": "bizapp.support.completed.v1",
  "eventTime": "2026-03-06T16:00:00.000+08:00",
  "payload": {
    "supportRequestId": "sreq_001",
    "requestNo": "SR20260304000001",
    "custId": "cust_1001",
    "createdBy": "emp_2001",
    "assignedEmpId": "emp_4001",
    "result": "SUCCESS",
    "summary": "已为客户制定方案，详见附件"
  }
}
```

---

## 9. 服务发现与注入

### 9.1 其他模块注入本模块 Api

其他模块（如 `performance-engine-center`）通过 Spring 依赖注入使用：

```java
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final LoanApi loanApi;
    private final SupportApi supportApi;
    private final BizApplyQueryApi bizApplyQueryApi;

    public void calculateMonthlyKpi(String empId) {
        BizApplyStatDTO stat = bizApplyQueryApi.getEmpStatisticsByPeriod(
            empId,
            firstDayOfMonth(),
            firstDayOfNextMonth()
        );
        // ...
    }
}
```

### 9.2 本模块 Api 实现注册

在 `business-application-center-impl` 中通过 `@Component` 注册，确保与其他模块在同一 Spring 上下文（当前 V1 为模块化单体，所有模块运行在同一 JVM）。

```java
@Component
@RequiredArgsConstructor
public class LoanApiImpl implements LoanApi {

    private final LoanApplyService loanApplyService;
    private final LoanConvert loanConvert;

    @Override
    public Optional<LoanApplyDTO> getLoanApply(String applyId) {
        if (!StringUtils.hasText(applyId)) {
            return Optional.empty();
        }
        LoanApply entity = loanApplyService.getById(applyId);
        return Optional.ofNullable(entity).map(loanConvert::toDTO);
    }

    @Override
    public Optional<LoanApplyDTO> getLoanApplyByBusinessKey(String businessKey) {
        if (!StringUtils.hasText(businessKey) || !businessKey.startsWith("LOAN:")) {
            return Optional.empty();
        }
        String applyId = businessKey.substring("LOAN:".length());
        return getLoanApply(applyId);
    }

    @Override
    public List<LoanApplyDTO> getCustomerLoanHistory(String custId) {
        if (!StringUtils.hasText(custId)) {
            return Collections.emptyList();
        }
        return loanConvert.toDTOList(loanApplyService.listByCustId(custId));
    }

    @Override
    public List<LoanApplyDTO> getLoanApplyBatch(List<String> applyIds) {
        if (applyIds == null || applyIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (applyIds.size() > 500) {
            throw new IllegalArgumentException("applyIds size cannot exceed 500");
        }
        return loanConvert.toDTOList(loanApplyService.listByIds(applyIds));
    }
}
```

---

## 10. 版本演进

### 10.1 兼容策略

- Api 接口变更遵循 **向后兼容** 原则
- 新增方法 → 直接追加
- 修改已有方法签名 → 不允许，只能新开同名 + `V2` 后缀方法
- 删除方法 → 至少经过 1 个大版本的 `@Deprecated` 标记
- DTO 字段只增不减；如需要删除字段，保留 1 个大版本的 `@Deprecated` + 空值返回

### 10.2 事件版本演进

- 事件 topic 带版本号（`.v1`, `.v2`）
- 载荷字段兼容原则同 DTO
- 新增事件 → 直接追加新 topic，不影响已有消费者
- 修改载荷字段结构 → 发布新版本 topic（如 `.v2`），旧版本保留至少 1 个大版本周期

---

## 11. 契约测试

### 11.1 生产者侧（本模块）

本模块提供契约测试套件 `business-application-center-contract-test`，包含：

- 所有 Api 方法的正常路径测试
- 异常路径测试（空入参、不存在的 ID、超限列表）
- DTO 字段非空约束测试

### 11.2 消费者侧

消费者模块（如 `report-analytics-center`）在本地通过 `@MockBean` mock Api 接口即可，不需要启动本模块。

### 11.3 集成测试

在 `bootstrap` 子模块中编写跨模块集成测试，启动完整 Spring 上下文后调用 Api，验证数据库真实查询结果。
