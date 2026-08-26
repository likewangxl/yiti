package com.bank.branch.platform.customer.dto.marketing.lead;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.Data;

/** 页面四工作流任务与线索展示数据。 */
@Data
public class LeadApprovalTaskResponse {
    private TaskRespDTO task;
    private Long leadId;
    private String leadNo;
    private String custName;
    private String unifiedCreditCode;
    private String leadSource;
    private String customerMatchStatus;
    private String leadStatus;
    private String importBatchNo;
    private MarketingCustomerSnapshot currentCustomer;
}
