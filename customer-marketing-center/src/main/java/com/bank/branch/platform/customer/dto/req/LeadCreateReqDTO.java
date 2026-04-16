package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建线索请求 DTO。
 */
@Data
public class LeadCreateReqDTO {

    /** 客户名称（必填） */
    @NotBlank(message = "客户名称不能为空")
    private String custName;

    /** 统一社会信用代码 */
    private String unifiedCreditCode;

    /** 联系人姓名 */
    private String contactPerson;

    /** 联系人手机号 */
    private String contactMobile;

    /** 行业分类（字典 INDUSTRY） */
    private String industry;

    /** 集团类型（字典 GROUP_TYPE） */
    private String groupType;

    /** 客户类型（字典 CUSTOMER_TYPE） */
    private String customerType;

    /** 是否重点客户：0-否/1-是 */
    private Integer isKeystone;

    /** 企业类型（字典 ENTERPRISE_TYPE） */
    private String enterpriseType;

    /** 所属集团名称 */
    private String groupName;

    /** 是否已开户：0-否/1-是 */
    private Integer isAccountOpened;

    /** 客户描述 */
    private String customerDesc;

    /** 授信金额（元） */
    private BigDecimal creditAmount;

    /** 授信敞口金额（元） */
    private BigDecimal creditExposureAmount;

    /** 线索来源（字典 LEAD_SOURCE） */
    private String leadSource;

    /** 标签ID列表（JSON数组，如 ["TAG_001","TAG_002"]） */
    private String tagIds;

    /** 备注 */
    private String remark;
}
