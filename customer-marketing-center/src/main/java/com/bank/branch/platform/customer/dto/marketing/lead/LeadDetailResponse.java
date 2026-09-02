package com.bank.branch.platform.customer.dto.marketing.lead;

import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import lombok.Data;

import java.util.List;

/** 线索详情，同时保留提交快照和当前主档展示。 */
@Data
public class LeadDetailResponse {
    private MarketingLeadInfo lead;
    private MarketingCustomerSnapshot currentCustomer;
    private boolean profileChanged;
    private List<String> managerEmpIds;
    private List<Long> tagIds;
    private List<FileObjectDTO> attachments;
}
