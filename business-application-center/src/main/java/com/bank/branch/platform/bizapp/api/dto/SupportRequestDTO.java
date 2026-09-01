package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中台支持申请对外传输对象。
 * <p>
 * 跨模块 API 契约：不携带 deleted 等内部运维字段；
 * 额外补充消费方所需的冗余展示字段（custName/productName/supportDeptName）。
 * </p>
 */
@Data
public class SupportRequestDTO {

    /** 来源类型：TOUCH_TASK / EXISTING_CUSTOMER。 */
    private String sourceType;

    /** 申请ID（UUID，32位去连字符） */
    private String id;

    /** 申请编号（SR+yyyyMMdd+6位序号） */
    private String requestNo;

    /** 同批提交分组ID（多产品拆单时共享） */
    private String submitGroupId;

    /** 客户ID */
    private String custId;

    /** 来源触达任务ID */
    private String sourceTouchTaskId;

    /** 产品ID */
    private String productId;

    /** 承接部门ORG_CODE */
    private String supportDeptId;

    /** 其他需求/补充说明 */
    private String otherDemand;

    /** 派单人工号（部门秘书，仅场景B） */
    private String dispatchEmpId;

    /** 派单时间 */
    private LocalDateTime dispatchTime;

    /** 承接办理人工号 */
    private String assignedEmpId;

    /** 状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED */
    private String status;

    /** 流程业务键，固定格式SUPPORT:{id} */
    private String businessKey;

    /** 流程实例ID */
    private String processInstanceId;

    /** 归属机构（发起侧ORG_CODE） */
    private String ownerOrgId;

    /** 创建人工号（发起人） */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新人工号 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    // ------------------------------------------------------------------
    // 冗余展示字段（由 SupportRequestDTOConverter 填充，不从 DB 直接映射）
    // ------------------------------------------------------------------

    /** 客户名称（冗余展示字段，由 CustomerQueryApi 填充） */
    private String custName;

    /** 产品名称（冗余展示字段，由 ProductApi 填充） */
    private String productName;

    /** 承接部门名称（冗余展示字段，由 OrgApi 填充） */
    private String supportDeptName;
}
