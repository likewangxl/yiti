package com.bank.branch.platform.customer.dto.marketing.lead;

import lombok.Data;

/** 提交审批后的流程关联结果。 */
@Data
public class LeadSubmitResponse {
    private Long leadId;
    private String leadStatus;
    private String processInstanceId;
    private String businessKey;
}
