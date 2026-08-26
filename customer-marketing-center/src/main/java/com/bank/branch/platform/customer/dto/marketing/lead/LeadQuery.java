package com.bank.branch.platform.customer.dto.marketing.lead;

import lombok.Data;

/** 页面三手工线索列表查询；leadSource 不接受前端覆盖，服务端固定 MANUAL。 */
@Data
public class LeadQuery {
    private String keyword;
    private String status;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
