package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户池汇总 VO（C.4 GET /customer-pool-summary，Task M3.3.1）.
 *
 * <p>来源：customer.CustomerQueryApi.countCustomers + customerType filter 分组累计.
 *
 * <p>V1.0 简化：3 类等级 VIP / NORMAL / POTENTIAL（与 customer 模块字典 customerType 字典项对齐，
 * 实际取值由 customer 模块决定，本 VO 仅传递）.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustPoolSummaryVO {

    /** 机构 ID */
    private String orgCode;

    /** 总客户数 */
    private Long totalCount;

    /** VIP 客户数 */
    private Long vipCount;

    /** 普通客户数 */
    private Long normalCount;

    /** 潜在客户数 */
    private Long potentialCount;
}
