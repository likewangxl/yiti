package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编辑线索请求 DTO。
 * <p>
 * 与 LeadCreateReqDTO 相同字段，但所有字段均非必填（null 表示不更新）。
 * </p>
 */
@Data
public class LeadUpdateReqDTO {

    private String leadType;

    private String custNo;

    /** 客户名称 */
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

    /** 是否限制触达：0-否/1-是，编辑时可选 */
    @Min(value = 0, message = "是否触达限制只能为0或1")
    @Max(value = 1, message = "是否触达限制只能为0或1")
    private Integer touchRestricted;

    /** 客户描述 */
    private String customerDesc;

    /** 授信金额（元） */
    private BigDecimal creditAmount;

    /** 授信敞口金额（元） */
    private BigDecimal creditExposureAmount;

    /** 线索来源（字典 LEAD_SOURCE） */
    private String leadSource;

    /** 标签ID列表（JSON数组） */
    private String tagIds;

    private List<String> tagIdList;

    private String distributionMode;

    private String mainManagerId;

    private List<String> managerScopeIds;

    private List<String> attachmentIds;

    /** 备注 */
    private String remark;
}
