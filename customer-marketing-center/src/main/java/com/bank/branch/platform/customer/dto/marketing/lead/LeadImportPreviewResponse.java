package com.bank.branch.platform.customer.dto.marketing.lead;

import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import lombok.Data;

import java.util.List;

/** 上传预览结果；预览只创建批次和明细，不生成线索。 */
@Data
public class LeadImportPreviewResponse {
    private MarketingLeadImportBatch batch;
    private List<MarketingLeadImportDetail> details;
}
