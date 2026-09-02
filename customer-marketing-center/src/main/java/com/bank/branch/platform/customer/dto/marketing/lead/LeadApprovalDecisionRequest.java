package com.bank.branch.platform.customer.dto.marketing.lead;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 线索审批动作；taskId 必须来自当前用户工作流待办。 */
@Data
public class LeadApprovalDecisionRequest {
    private String taskId;

    @Size(max = 500, message = "审批意见长度不能超过500")
    private String opinion;
}
