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
 * <p>装配字段（V1.14 # 2 后 6 个区块）：
 * <ul>
 *   <li>{@code stats}：V1.14 # 2 新增 — 5 项 KPI 卡有序数组（前端 {@code Dashboard.vue} 直接 v-for 渲染）</li>
 *   <li>{@code summaryMetrics}：旧字段保留向后兼容（{@link com.bank.branch.platform.report.config.DashboardPresidentMetrics#CORE_METRICS} 一次性查询结果）</li>
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

    /**
     * KPI 卡有序数组（V1.14 # 2 新增），与前端
     * {@code xanzc_frontend/src/views/report/Dashboard.vue} 5 项 KPI 卡 v-for 模板 1:1 对齐.
     *
     * <p>顺序固定为 {@link com.bank.branch.platform.report.config.DashboardPresidentMetrics#KPI_CARD_METRICS}：
     * 存款日均 / 贷款余额 / 不良贷款率 / 中间业务收入 / 本月新增有效客户.
     */
    private List<KpiCardItem> stats;

    /** 全行存款趋势（12 月折线图） */
    private ChartDataDTO depositTrend;

    /** 全行贷款趋势（12 月折线图） */
    private ChartDataDTO loanTrend;

    /** 机构业绩排名（前 20） */
    private List<OrgRankingItemDTO> orgRanking;

    /** Top 客户贡献（前 10） */
    private List<TopCustomerDTO> topCustomers;

    /**
     * 汇总指标（{@link com.bank.branch.platform.report.config.DashboardPresidentMetrics#CORE_METRICS}
     * 单次查询结果）.
     *
     * <p>V1.14 # 2 之后建议下游优先消费 {@link #stats} 字段（含 label/unit/trend 渲染元数据），
     * 本字段保留以兼容旧调用方.
     */
    private Map<String, BigDecimal> summaryMetrics;
}
