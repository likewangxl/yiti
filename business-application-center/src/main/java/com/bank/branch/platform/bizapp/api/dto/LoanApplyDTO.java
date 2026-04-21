package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产投放申请对外传输对象。
 * <p>
 * 跨模块 API 契约：不携带 deleted 等内部运维字段；
 * 额外补充消费方所需的冗余展示字段（custName 等）。
 * </p>
 */
@Data
public class LoanApplyDTO {

    /** 申请ID（UUID，32位去连字符） */
    private String id;

    /** 申请编号（LA+yyyyMMdd+6位序号） */
    private String applyNo;

    /** 客户ID */
    private String custId;

    /** 来源触达任务ID */
    private String sourceTouchTaskId;

    /** 项目类型（字典PROJECT_TYPE） */
    private String projectType;

    /** 业务类型（字典BIZ_TYPE） */
    private String bizType;

    /** 担保方式（字典GUARANTEE_TYPE） */
    private String guaranteeType;

    /** 授信金额（元，保留4位小数） */
    private BigDecimal creditAmount;

    /** 敞口金额（元，保留4位小数） */
    private BigDecimal creditExposureAmount;

    /** 状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED */
    private String status;

    /** 流程业务键，固定格式LOAN:{id} */
    private String businessKey;

    /** 流程实例ID */
    private String processInstanceId;

    /** 归属机构（ORG_CODE） */
    private String ownerOrgId;

    /** 创建人工号 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新人工号 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    // ------------------------------------------------------------------
    // 冗余展示字段（由 LoanApplyDTOConverter 填充，不从 DB 直接映射）
    // ------------------------------------------------------------------

    /** 客户名称（冗余展示字段，由 CustomerQueryApi 填充） */
    private String custName;
}
