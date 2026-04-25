package com.bank.branch.platform.report.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DashboardPresidentMetrics} 守护测试（Task M2.1.1，Red）.
 *
 * <p>守护点（对照 08 §6.1 预置指标硬编码清单）：
 * <ul>
 *   <li>5 类常量（CORE / ACHIEVEMENT / TREND / RANKING / CUST_CONTRIBUTION）数量与编码精确匹配</li>
 *   <li>所有 List 不可变（{@code List.of(...)} 风格），向其 add 抛 {@link UnsupportedOperationException}</li>
 * </ul>
 *
 * <p>V2 计划迁移到 {@code rpt_dashboard_def} 表后本测试退化为契约对齐守护.
 */
class DashboardPresidentMetricsTest {

    @Test
    void coreMetrics_shouldHave4Items_inOrder() {
        assertThat(DashboardPresidentMetrics.CORE_METRICS)
                .containsExactly("DEP_BAL_ORG_DAILY", "LOAN_BAL_ORG_DAILY",
                        "INT_INCOME_ORG_MONTH", "FEE_INCOME_ORG_MONTH");
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
        assertThat(DashboardPresidentMetrics.RANKING_METRICS)
                .containsExactly("KPI_TOTAL_SCORE_ORG", "DEP_BAL_ORG_DAILY");
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
