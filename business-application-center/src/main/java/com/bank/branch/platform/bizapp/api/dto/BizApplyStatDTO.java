package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工业务申请统计 DTO。
 * <p>
 * 用于 {@code BizApplyQueryApi.getEmpStatistics()} 和
 * {@code BizApplyQueryApi.getEmpStatisticsByPeriod()} 的返回结果。
 * 聚合贷款申请和中台支持申请两个域的数量与金额。
 * </p>
 */
@Data
public class BizApplyStatDTO {

    /** 贷款申请总数 */
    private long totalLoans;

    /** 已完成的贷款申请数 */
    private long completedLoans;

    /** 支持申请总数 */
    private long totalSupports;

    /** 已完成的支持申请数 */
    private long completedSupports;

    /** 授信总金额（元，汇总所有已完成贷款申请的 credit_amount） */
    private BigDecimal totalCreditAmount;
}
