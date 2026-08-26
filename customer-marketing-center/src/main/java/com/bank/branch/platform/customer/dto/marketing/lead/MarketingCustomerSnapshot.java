package com.bank.branch.platform.customer.dto.marketing.lead;

import lombok.Data;

import java.math.BigDecimal;

/** 审批/录入详情反显的客户主档只读快照。 */
@Data
public class MarketingCustomerSnapshot {
    private Long id;
    private String custNo;
    private String custName;
    private String unifiedCreditCode;
    private String legalRepresentative;
    private BigDecimal registeredCapital;
    private String registeredAddress;
    private String businessAddress;
    private String businessScope;
    private String contactPerson;
    private String contactMobile;
    private String industry;
    private String groupType;
    private String groupName;
    private String customerType;
    private String enterpriseType;
    private Integer isKeystone;
    private Integer isAccountOpened;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String mainManagerId;
    private String mainOrgId;
    private String ownershipStatus;
    private Integer profileVersion;
}
