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
 * 分行行长仪表盘响应 DTO（03 §C.1 PresidentDashboardRespDTO）.
 *
 * <p>装配字段（5 个区块）：
 * <ul>
 *   <li>{@code summaryMetrics}：核心 4 指标（顶部 KPI 区）— 来自 {@link com.bank.branch.platform.report.config.DashboardPresidentMetrics#CORE_METRICS}</li>
 *   <li>{@code depositTrend} / {@code loanTrend}：12 个月趋势折线图</li>
 *   <li>{@code orgRanking}：下属机构业绩排行（V1.0 取 V1 子机构 + 单层）</li>
 *   <li>{@code topCustomers}：Top 10 贡献度客户</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresidentDashboardRespDTO {

    /** 数据日期 */
    private LocalDate dataDate;

    /** sys_control 数据版本（V1.0 暂为 null，留 V2 接入版本控制后填充） */
    private String dataVersion;

    /** 全行存款趋势（12 月折线图） */
    private ChartDataDTO depositTrend;

    /** 全行贷款趋势（12 月折线图） */
    private ChartDataDTO loanTrend;

    /** 机构业绩排名（前 20） */
    private List<OrgRankingItemDTO> orgRanking;

    /** Top 客户贡献（前 10） */
    private List<TopCustomerDTO> topCustomers;

    /** 汇总指标（CORE_METRICS 单次查询结果） */
    private Map<String, BigDecimal> summaryMetrics;
}
