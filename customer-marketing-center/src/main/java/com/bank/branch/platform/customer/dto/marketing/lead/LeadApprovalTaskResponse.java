package com.bank.branch.platform.customer.dto.marketing.lead;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.Data;

import java.time.LocalDateTime;

/** 页面四工作流任务与线索展示数据。 */
@Data
public class LeadApprovalTaskResponse {
    private TaskRespDTO task;
    private Long leadId;
    private String leadNo;
    private String custName;
    private String unifiedCreditCode;
    private String leadType;
    private String leadSource;
    private String industry;
    private String distributionMode;
    private String customerMatchStatus;
    private String leadStatus;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private String importBatchNo;
    private MarketingCustomerSnapshot currentCustomer;
}
