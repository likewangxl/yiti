package com.bank.branch.platform.customer.dto.marketing.customer;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户营销域内部的机构聚合行，不作为跨模块契约返回。
 */
@Data
public class MarketingOrgSnapshotRow {
    private String orgCode;
    private Long validCustomerCount;
    private Long pendingFollowUpTaskCount;
    private LocalDateTime sourceUpdatedAt;
}
