package com.bank.branch.platform.customer.dto.marketing.tag;

import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportBatch;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 页面六按标签聚合的待审批摘要。 */
@Data
public class TagApprovalSummary {
    private MarketingCustomerTag tag;
    private List<MarketingCustomerTagImportBatch> batches = new ArrayList<>();
    private int pendingCustomerCount;
    private int approvedCustomerCount;
    private int rejectedCustomerCount;
}
