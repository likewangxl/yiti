package com.bank.branch.platform.report.config;

import java.util.List;

/**
 * 分行行长仪表盘 V1 预置指标（硬编码，08 §6.1）.
 *
 * <p>V1 选择硬编码而非从 {@code rpt_dashboard_def} 表读取的原因：
 * <ul>
 *   <li>避免首次部署需配置元数据，降低部署复杂度</li>
 *   <li>仪表盘指标变化频率低，硬编码足以满足 V1 需求</li>
 * </ul>
 *
 * <p>V2 计划迁移到 {@code rpt_dashboard_def} 表，支持不同分行/角色定制.
 */
public final class DashboardPresidentMetrics {

    private DashboardPresidentMetrics() {
        // 工具类禁止实例化
    }

    /** 核心指标（顶部 KPI 区） */
    public static final List<String> CORE_METRICS = List.of(
            "DEP_BAL_ORG_DAILY",      // 全行存款日均余额
            "LOAN_BAL_ORG_DAILY",     // 全行贷款日均余额
            "INT_INCOME_ORG_MONTH",   // 本月净利息收入
            "FEE_INCOME_ORG_MONTH"    // 本月中间业务收入
    );

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
            "DEP_BAL_ORG_DAILY"       // 存款余额
    );

    /** Top 客户贡献指标 */
    public static final List<String> CUST_CONTRIBUTION_METRICS = List.of(
            "AUM_TOTAL_CUST",         // 客户 AUM
            "PROFIT_CONTRIB_CUST"     // 客户利润贡献
    );
}
