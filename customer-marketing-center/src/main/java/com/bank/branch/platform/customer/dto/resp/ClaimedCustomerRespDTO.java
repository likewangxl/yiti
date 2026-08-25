package com.bank.branch.platform.customer.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

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
}
