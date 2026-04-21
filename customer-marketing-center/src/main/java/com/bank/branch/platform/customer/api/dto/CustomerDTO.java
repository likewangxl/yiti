package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户对外 DTO
 * <p>
 * 用于跨模块传递客户基本信息，包含标签列表、授信信息及来源机构等字段。
 * 禁止携带内部实体引用，仅包含可对外暴露的字段。
 * </p>
 */
@Data
public class CustomerDTO {

    /** 客户 ID */
    private String id;

    /** 客户编号 */
    private String custNo;

    /** 客户名称 */
    private String custName;

    /** 统一社会信用代码 */
    private String unifiedCreditCode;

    /** 行业代码 */
    private String industry;

    /** 行业名称 (字典翻译) */
    private String industryName;

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

    /** 客户描述 */
    private String customerDesc;

    /** 授信金额 */
    private BigDecimal creditAmount;

    /** 授信敞口 */
    private BigDecimal creditExposureAmount;

    /** 来源机构 ID (仅展示用) */
    private String ownerOrgId;

    /** 来源机构名称 */
    private String ownerOrgName;

    /** 关联来源线索 ID */
    private String leadId;

    /** 客户状态: VALID / DELETED */
    private String status;

    /** 标签 ID 列表 */
    private List<String> tagIds;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
