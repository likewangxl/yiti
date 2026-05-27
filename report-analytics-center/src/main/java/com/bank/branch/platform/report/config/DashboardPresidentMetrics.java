package com.bank.branch.platform.report.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分行行长仪表盘 V1 预置指标（硬编码，08 §6.1）.
 *
 * <p>V1 选择硬编码而非从 {@code rpt_dashboard_def} 表读取的原因：
 * <ul>
 *   <li>避免首次部署需配置元数据，降低部署复杂度</li>
 *   <li>仪表盘指标变化频率低，硬编码足以满足 V1 需求</li>
 * </ul>
 *
 * <p>V1.14 # 2 修订：
 * <ul>
 *   <li>新增 {@link #KPI_CARD_METRICS}（5 项有序 {@link KpiCardMeta}），与
 *       {@code xanzc_frontend/src/views/report/Dashboard.vue} 5 项 KPI 卡前端 mock 契约 1:1 对齐</li>
 *   <li>{@link #CORE_METRICS} 重定义为 {@link #KPI_CARD_METRICS} 的 metric_code 列（向后兼容旧调用）；
 *       原 {@code INT_INCOME_ORG_MONTH}（净利息收入）因前端不展示移除，改入 {@code NPL_RATIO_ORG} /
 *       {@code NEW_VALID_CUST_ORG_MONTH}</li>
 *   <li>metric_code 命名向 docs/modules/performance-engine-center/08-初始化数据清单.md 看齐：
 *       {@code DEP_BAL_ORG_DAILY} → {@code DEP_BAL_ORG}、{@code LOAN_BAL_ORG_DAILY} → {@code LOAN_BAL_ORG}</li>
 * </ul>
 *
 * <p>V2 计划迁移到 {@code rpt_dashboard_def} 表，支持不同分行/角色定制.
 */
public final class DashboardPresidentMetrics {

    private DashboardPresidentMetrics() {
        // 工具类禁止实例化
    }

    /**
     * KPI 卡片有序元数据（V1.14 # 2 新增）.
     *
     * <p>5 项卡按前端 {@code xanzc_frontend/src/views/report/Dashboard.vue} 模板循环顺序排列：
     * 存款日均 / 贷款余额 / 不良贷款率 / 中间业务收入 / 本月新增有效客户.
     */
    public static final List<KpiCardMeta> KPI_CARD_METRICS = List.of(
            new KpiCardMeta("DEP_BAL_ORG",                "存款日均",         "亿"),
            new KpiCardMeta("LOAN_BAL_ORG",               "贷款余额",         "亿"),
            new KpiCardMeta("NPL_RATIO_ORG",              "不良贷款率",       "%"),
            new KpiCardMeta("FEE_INCOME_ORG_MONTH",       "中间业务收入",     "万"),
            new KpiCardMeta("NEW_VALID_CUST_ORG_MONTH",   "本月新增有效客户", "")
    );

    /**
     * 核心指标 metric_code 列表（顶部 KPI 区，V1.14 # 2 后定义为 {@link #KPI_CARD_METRICS} 的 metric_code 投影）.
     *
     * <p>保留此常量是为了向后兼容旧调用方（{@link com.bank.branch.platform.report.service.impl.DashboardServiceImpl}
     * 使用其作为 {@code MetricApi.getOrgMetricValues} 的入参）.
     */
    public static final List<String> CORE_METRICS = KPI_CARD_METRICS.stream()
            .map(KpiCardMeta::getMetricCode)
            .collect(Collectors.toUnmodifiableList());

    /** 达成率指标（进度条区） */
    public static final List<String> ACHIEVEMENT_METRICS = List.of(
            "DEP_ACHIEVE_RATE_ORG",   // 存款达成率
            "LOAN_ACHIEVE_RATE_ORG",  // 贷款达成率
            "NEW_CUST_ACHIEVE_ORG",   // 新增客户达成率
            "KPI_ACHIEVE_RATE_ORG"    // 综合绩效达成率
    );

    /** 对比指标（同比/环比） */
    public static final List<String> TREND_METRICS = List.of(
            "DEP_BAL_YOY_RATE",       // 存款余额同比增长率
            "LOAN_BAL_YOY_RATE",      // 贷款余额同比增长率
            "DEP_BAL_MOM_RATE",       // 存款余额环比增长率
            "LOAN_BAL_MOM_RATE"       // 贷款余额环比增长率
    );

    /** 排行榜指标（下属机构排名） */
    public static final List<String> RANKING_METRICS = List.of(
            "KPI_TOTAL_SCORE_ORG",    // 综合得分
            "DEP_BAL_ORG"             // 存款余额（V1.14 # 2 重命名 DEP_BAL_ORG_DAILY → DEP_BAL_ORG）
    );

    /** Top 客户贡献指标 */
    public static final List<String> CUST_CONTRIBUTION_METRICS = List.of(
            "AUM_TOTAL_CUST",         // 客户 AUM
            "PROFIT_CONTRIB_CUST"     // 客户利润贡献
    );

    /**
     * KPI 卡片元数据（V1.14 # 2 新增）.
     *
     * <p>每项卡的 {@code metricCode} / {@code label} / {@code unit} 三要素，
     * 用于 {@code DashboardServiceImpl.buildStats(...)} 组装
     * {@link com.bank.branch.platform.report.dto.resp.KpiCardItem}.
     */
    @Getter
    @AllArgsConstructor
    public static final class KpiCardMeta {

        /** 指标编码，对接 {@code performance.MetricApi.getOrgMetricValues} */
        private final String metricCode;

        /** 卡片中文标题 */
        private final String label;

        /** 单位（如 "亿"、"%"、空串） */
        private final String unit;
    }
}
