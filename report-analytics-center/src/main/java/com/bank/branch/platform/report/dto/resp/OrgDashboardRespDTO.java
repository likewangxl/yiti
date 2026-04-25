package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 机构仪表盘响应 DTO（C.2 GET /dashboard/org/{orgCode}，Task M2.3.1）.
 *
 * <p>V1.0 简化字段集（按 ORG_SUBTREE 维度展示子机构数据）：
 * <ul>
 *   <li>orgCode / orgName</li>
 *   <li>dataDate</li>
 *   <li>summaryMetrics：CORE_METRICS 单次查询</li>
 *   <li>achievementMetrics：ACHIEVEMENT_METRICS 达成率</li>
 *   <li>subOrgRanking：直属子机构排名</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDashboardRespDTO {

    /** 机构 ID（orgCode） */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 数据日期 */
    private LocalDate dataDate;

    /** 汇总指标（CORE_METRICS） */
    private Map<String, BigDecimal> summaryMetrics;

    /** 达成率指标（ACHIEVEMENT_METRICS） */
    private Map<String, BigDecimal> achievementMetrics;

    /** 直属子机构排名（按业绩倒序） */
    private List<OrgRankingItemDTO> subOrgRanking;
}
