package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线索对外 DTO
 * <p>
 * 用于跨模块传递客户线索信息，包含版本管理字段、审批状态及导入批次等。
 * 线索是客户的前置数据形态，每次修改均产生新版本记录。
 * </p>
 */
@Data
public class LeadDTO {

    /** 线索 ID */
    private String id;

    /** 客户名称 */
    private String custName;

    /** 统一社会信用代码 */
    private String unifiedCreditCode;

    /** 行业代码 */
    private String industry;

    /** 集团归属 */
    private String groupType;

    /** 客户类型 */
    private String customerType;

    /** 是否重点客户 */
    private Boolean isKeystone;

    /** 企业性质 */
    private String enterpriseType;

    /** 是否已开户 */
    private Boolean isAccountOpened;

    /** 是否限制触达；1/true 为受限 */
    private Boolean touchRestricted;

    /** 客户描述 */
    private String customerDesc;

    /** 授信金额 */
    private BigDecimal creditAmount;

    /** 授信敞口 */
    private BigDecimal creditExposureAmount;

    /** 线索操作类型: CREATE / UPDATE / DELETE */
    private String leadOp;

    /** 来源客户 ID */
    private String sourceCustId;

    /** 上一版本线索 ID */
    private String prevLeadId;

    /** 版本号 */
    private Integer versionNo;

    /** 是否为最新版本 */
    private Boolean isLatest;

    /** 线索审批状态: DRAFT / PENDING_APPROVAL / APPROVED / REJECTED */
    private String leadStatus;

    /** 工作流业务键 */
    private String businessKey;

    /** 导入批次 ID */
    private String importBatchId;

    /** 归属机构 ID */
    private String ownerOrgId;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
