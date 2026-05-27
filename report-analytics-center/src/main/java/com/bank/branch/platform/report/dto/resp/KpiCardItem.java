package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分行行长仪表盘 KPI 卡片单项（V1.14 # 2 新增）.
 *
 * <p>与前端 {@code xanzc_frontend/src/views/report/Dashboard.vue} 5 项 KPI 卡片
 * 模板循环结构 {@code v-for="s in data.stats"} 一一对齐，{@link #label} / {@link #value}
 * / {@link #unit} / {@link #trend} / {@link #trendType} 直接被前端渲染.
 *
 * <p>取值约定：
 * <ul>
 *   <li>{@link #value} 当上游 {@code MetricApi.getOrgMetricValues} 未返回该 metric_code 时为 {@code null}，
 *   前端 Vue 模板 {@code {{ value }}} 输出空字符串不报错</li>
 *   <li>{@link #trend} 缺失或环比基准为 0 时返回 {@code "--"}，{@link #trendType} 同步置 {@code "flat"}</li>
 *   <li>{@link #metricCode} 用于前端 debug + 后续按 metric_code 二次查询的回程定位</li>
 * </ul>
 *
 * @see com.bank.branch.platform.report.config.DashboardPresidentMetrics#KPI_CARD_METRICS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiCardItem {

    /** 指标编码，如 {@code DEP_BAL_ORG} */
    private String metricCode;

    /** 卡片中文标题，如 "存款日均" */
    private String label;

    /** 当日值，缺失时 {@code null}（前端 UI 显示 "--"） */
    private BigDecimal value;

    /** 单位，如 "亿"、"%"、""（无单位时空串） */
    private String unit;

    /** 趋势文案，如 "↑ 较月初 +3.2%" / "↓ 较月初 -0.8%" / "--" */
    private String trend;

    /** 趋势类型：up / down / flat（前端用作 CSS class） */
    private String trendType;
}
