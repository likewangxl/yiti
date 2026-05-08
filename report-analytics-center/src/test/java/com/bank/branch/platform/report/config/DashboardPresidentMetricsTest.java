package com.bank.branch.platform.report.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DashboardPresidentMetrics} 守护测试.
 *
 * <p>守护点（对照 08 §6.1 预置指标硬编码清单 + V1.14 # 2 KPI 卡契约）：
 * <ul>
 *   <li>5 类常量（CORE / ACHIEVEMENT / TREND / RANKING / CUST_CONTRIBUTION）数量与编码精确匹配</li>
 *   <li>V1.14 # 2 新增：{@code KPI_CARD_METRICS}（5 项有序 KpiCardMeta），与
 *       {@code xanzc_frontend/src/views/report/Dashboard.vue} 5 项 KPI 卡前端 mock 契约 1:1 对齐</li>
 *   <li>所有 List 不可变（{@code List.of(...)} 风格），向其 add 抛 {@link UnsupportedOperationException}</li>
 *   <li>metric_code 命名向 docs/modules/performance-engine-center/08-初始化数据清单.md 看齐
 *       （V1.14 # 2 把旧 {@code DEP_BAL_ORG_DAILY} 重命名为 {@code DEP_BAL_ORG}）</li>
 * </ul>
 *
 * <p>V2 计划迁移到 {@code rpt_dashboard_def} 表后本测试退化为契约对齐守护.
 */
class DashboardPresidentMetricsTest {

    @Test
    void coreMetrics_shouldHave5Items_alignedToKpiCardMetrics() {
        // V1.14 # 2 后 CORE_METRICS 重定义为 KPI_CARD_METRICS 的 metric_code 列（向后兼容旧调用）
        assertThat(DashboardPresidentMetrics.CORE_METRICS)
                .containsExactly("DEP_BAL_ORG", "LOAN_BAL_ORG",
                        "NPL_RATIO_ORG", "FEE_INCOME_ORG_MONTH",
                        "NEW_VALID_CUST_ORG_MONTH");
    }

    @Test
    void kpiCardMetrics_shouldHave5Items_inOrderAlignedWithFrontend() {
        // 与 xanzc_frontend/src/views/report/Dashboard.vue 5 项卡 v-for="s in data.stats" 顺序一致
        assertThat(DashboardPresidentMetrics.KPI_CARD_METRICS)
                .hasSize(5)
                .extracting(DashboardPresidentMetrics.KpiCardMeta::getMetricCode)
                .containsExactly("DEP_BAL_ORG", "LOAN_BAL_ORG",
                        "NPL_RATIO_ORG", "FEE_INCOME_ORG_MONTH",
                        "NEW_VALID_CUST_ORG_MONTH");
    }

    @Test
    void kpiCardMetrics_label_shouldMatchFrontendMock() {
        // 与 xanzc_frontend/src/mock/index.js:127-150 的 stats[*].label 顺序一致
        assertThat(DashboardPresidentMetrics.KPI_CARD_METRICS)
                .extracting(DashboardPresidentMetrics.KpiCardMeta::getLabel)
                .containsExactly("存款日均", "贷款余额", "不良贷款率",
                        "中间业务收入", "本月新增有效客户");
    }

    @Test
    void kpiCardMetrics_unit_shouldMatchFrontendMock() {
        // 与 xanzc_frontend/src/mock/index.js:127-150 的 stats[*].unit 顺序一致
        assertThat(DashboardPresidentMetrics.KPI_CARD_METRICS)
                .extracting(DashboardPresidentMetrics.KpiCardMeta::getUnit)
                .containsExactly("亿", "亿", "%", "万", "");
    }

    @Test
    void achievementMetrics_shouldHave4Items_inOrder() {
        assertThat(DashboardPresidentMetrics.ACHIEVEMENT_METRICS)
                .containsExactly("DEP_ACHIEVE_RATE_ORG", "LOAN_ACHIEVE_RATE_ORG",
                        "NEW_CUST_ACHIEVE_ORG", "KPI_ACHIEVE_RATE_ORG");
    }

    @Test
    void trendMetrics_shouldHave4Items_inOrder() {
        assertThat(DashboardPresidentMetrics.TREND_METRICS)
                .containsExactly("DEP_BAL_YOY_RATE", "LOAN_BAL_YOY_RATE",
                        "DEP_BAL_MOM_RATE", "LOAN_BAL_MOM_RATE");
    }

    @Test
    void rankingMetrics_shouldHave2Items_inOrder() {
        // V1.14 # 2 metric_code 重命名：DEP_BAL_ORG_DAILY → DEP_BAL_ORG（docs 对齐）
        assertThat(DashboardPresidentMetrics.RANKING_METRICS)
                .containsExactly("KPI_TOTAL_SCORE_ORG", "DEP_BAL_ORG");
    }

    @Test
    void custContributionMetrics_shouldHave2Items_inOrder() {
        assertThat(DashboardPresidentMetrics.CUST_CONTRIBUTION_METRICS)
                .containsExactly("AUM_TOTAL_CUST", "PROFIT_CONTRIB_CUST");
    }

    @Test
    void allMetricsLists_shouldBeImmutable() {
        // 防御 List.of() 之外的可变实现
        assertThatThrownBy(() -> DashboardPresidentMetrics.CORE_METRICS.add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DashboardPresidentMetrics.KPI_CARD_METRICS.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DashboardPresidentMetrics.ACHIEVEMENT_METRICS.add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DashboardPresidentMetrics.TREND_METRICS.add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DashboardPresidentMetrics.RANKING_METRICS.add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> DashboardPresidentMetrics.CUST_CONTRIBUTION_METRICS.add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
