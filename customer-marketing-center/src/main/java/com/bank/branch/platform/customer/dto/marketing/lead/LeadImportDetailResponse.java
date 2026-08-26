package com.bank.branch.platform.customer.dto.marketing.lead;

import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import lombok.Data;

/** 导入明细分页响应。 */
@Data
public class LeadImportDetailResponse {
    private MarketingLeadImportDetail detail;
    private String statusName;
}
