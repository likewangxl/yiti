package com.bank.branch.platform.customer.dto.marketing.lead;

import lombok.Data;

/** 线索批次列表查询。 */
@Data
public class LeadImportQuery {
    private String keyword;
    private String status;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
