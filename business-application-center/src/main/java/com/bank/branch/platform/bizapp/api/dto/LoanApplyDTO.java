package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产投放申请对外传输对象。
 * <p>
 * 镜像 {@code LoanApply} 实体字段，用于跨模块数据传递。
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

    /** 逻辑删除：0=未删，1=已删 */
    private Integer deleted;
}
