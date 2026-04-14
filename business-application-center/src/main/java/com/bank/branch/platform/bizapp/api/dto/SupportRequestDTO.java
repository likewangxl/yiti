package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中场支持申请对外传输对象。
 * <p>
 * 镜像 {@code SupportRequest} 实体字段，用于跨模块数据传递。
 * </p>
 */
@Data
public class SupportRequestDTO {

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

    /** 逻辑删除 */
    private Integer deleted;
}
