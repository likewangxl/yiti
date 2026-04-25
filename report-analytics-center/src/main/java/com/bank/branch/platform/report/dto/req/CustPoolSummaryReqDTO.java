package com.bank.branch.platform.report.dto.req;

import lombok.Data;

/**
 * 客户池汇总入参 DTO（C.4 GET /customer-pool-summary，Task M3.3.1）.
 *
 * <p>业务规则（plan L2144-L2148 + docs 03 §C.4）：
 * <ul>
 *   <li>orgId 可选：为空时按当前用户主机构兜底</li>
 *   <li>分组维度：客户等级（VIP / 普通 / 潜在），由 customer.CustomerQueryApi.countCustomers + filter.customerTypes 多次查询累计</li>
 * </ul>
 */
@Data
public class CustPoolSummaryReqDTO {

    /** 机构 ID（可选，为空时按当前用户主机构兜底） */
    private String orgId;
}
