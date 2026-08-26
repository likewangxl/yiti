package com.bank.branch.platform.customer.dto.marketing.lead;

import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import lombok.Data;

/** 线索详情，同时保留提交快照和当前主档展示。 */
@Data
public class LeadDetailResponse {
    private MarketingLeadInfo lead;
    private MarketingCustomerSnapshot currentCustomer;
    private boolean profileChanged;
}
