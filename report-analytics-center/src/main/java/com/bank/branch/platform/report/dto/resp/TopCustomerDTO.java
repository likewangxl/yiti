package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Top 客户贡献度条目（03 §C.1 TopCustomerDTO）.
 *
 * <p>客户名按 {@code common-dev-guide.md} §8 数据脱敏规则脱敏（保留首字 + **）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopCustomerDTO {

    /** 排名（从 1 开始） */
    private Integer rank;

    /** 客户 ID */
    private String customerId;

    /** 客户名（脱敏后） */
    private String customerName;

    /** 贡献度分值 */
    private BigDecimal contributionScore;

    /** 存款余额 */
    private BigDecimal depositBal;

    /** AUM（资产管理规模） */
    private BigDecimal aum;
}
