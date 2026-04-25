package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 机构业绩排名条目（03 §C.1 OrgRankingItemDTO）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgRankingItemDTO {

    /** 排名（从 1 开始） */
    private Integer rank;

    /** 机构 ID（orgCode） */
    private String orgId;

    /** 机构名称 */
    private String orgName;

    /** 达成率（百分比） */
    private BigDecimal achievementRate;

    /** 目标值 */
    private BigDecimal target;

    /** 实际值 */
    private BigDecimal actual;
}
