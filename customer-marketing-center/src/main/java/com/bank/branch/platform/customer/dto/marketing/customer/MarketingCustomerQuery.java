package com.bank.branch.platform.customer.dto.marketing.customer;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 营销客户列表查询条件。排序字段由后端固定，不接受前端 SQL/列名。 */
@Data
public class MarketingCustomerQuery {

    private String keyword;
    private String unifiedCreditCode;
    private String custNo;
    private String mainManagerId;
    private String ownershipStatus;
    private Integer isAccountOpened;
    private String industry;
    private String customerType;
    private Integer isKeystone;
    private String ownershipMaintainMode;
    private String recordStatus;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedTo;

    private int pageNo = 1;
    private int pageSize = 20;
}
