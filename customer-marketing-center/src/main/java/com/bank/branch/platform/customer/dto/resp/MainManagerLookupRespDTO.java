package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

/** 录入线索前查询存量客户及主办权的结果。 */
@Data
public class MainManagerLookupRespDTO {
    private Boolean existingCustomer;
    private String customerId;
    private String custNo;
    private String custName;
    private String unifiedCreditCode;
    private Boolean hasMainOwnership;
    private String mainManagerId;
    private String mainManagerName;
    private String mainOrgId;
    private String mainOrgName;
}
