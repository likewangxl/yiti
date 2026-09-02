package com.bank.branch.platform.customer.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 已认领客户列表行。 */
@Data
@Schema(description = "已认领客户列表行")
public class ClaimedCustomerRespDTO {
    private String claimId;
    private String custId;
    private String custNo;
    private String custName;
    private String unifiedCreditCode;
    private String contactPerson;
    private String contactMobile;
    private String industry;
    private String industryName;
    private String customerType;
    private String customerTypeName;
    private String ownerOrgId;
    private String ownerOrgName;
    private String orgId;
    private String maintainerEmpId;
    private LocalDateTime claimTime;
    private LocalDateTime lastTouchTime;
    private String latestTaskId;
    private String latestTaskNo;
    private String latestTaskStatus;
    private String latestSlaStatus;

    /** 目标营销表字段；保留 String 形态兼容既有 REST 消费方。 */
    private String sourceLeadId;
    private String leadNo;
    private String distributionMode;
    private String sourceType;
    private String allocationSource;
    private String claimStatus;
    private String cancelReason;
    private String leadStatus;

    private String legalRepresentative;
    private BigDecimal registeredCapital;
    private String registeredAddress;
    private String businessAddress;
    private String businessScope;
    private String groupType;
    private String groupName;
    private String enterpriseType;
    private Boolean isKeystone;
    private Boolean isAccountOpened;
    private Boolean touchRestricted;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String customerDesc;
    private List<String> tagNames;
}
