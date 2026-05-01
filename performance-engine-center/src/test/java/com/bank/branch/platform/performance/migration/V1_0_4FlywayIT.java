package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_0_4 Flyway 迁移集成测试.
 *
 * <p>背景：V1_0_4 脚本原本将 V1.1/V1.2 规划但尚未实现的 10 条 PT_RESOURCE
 * 记录标记为禁用（STATUS=1），避免 V1.0 对外误授权未实现端点。
 *
 * <p>V1.1/V1.2 迭代过程中，这 10 条资源陆续通过 V1_1_1 / V1_2_2 / V1_2_4
 * 激活（STATUS 1→0）。本 IT 在 Q8.6 后调整为"名单存在"+"V1_0_1 基线启用"
 * 两项不变语义，以避免与后续版本耦合。
 *
 * <p>测试环境：本地 MySQL onepl_test_bootstrap（Docker 不可用，降级）。
 */
class V1_0_4FlywayIT extends PerformanceFlywayTestBase {

    /**
     * 验证 V1_0_4 登记的 10 条 V1.1/V1.2 规划资源始终存在（不论 STATUS 值）.
     *
     * <p>V1_2_4（Q8.6）激活最后 7 条后，全部 10 条都应 STATUS=0，
     * 但无论后续 migration 如何，RESOURCE_ID 名单必须完整保留。
     */
    @Test
    void pendingResources_areRegistered() {
        List<String> ids = jdbc.queryForList(
            "SELECT RESOURCE_ID FROM PT_RESOURCE " +
            "WHERE RESOURCE_ID IN (" +
            "  'P_PERF_METRIC_EXEC','P_PERF_METRIC_TRIAL'," +
            "  'P_PERF_IMPORT_UPLOAD','P_PERF_ALLOC_ADJ_ADD'," +
            "  'P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC'," +
            "  'P_PERF_DTASK_STATUS','P_PERF_SC_ROLLBACK'," +
            "  'P_PERF_EXPORT_KPI','P_PERF_EXPORT_ALLOC'" +
            ") " +
            "ORDER BY RESOURCE_ID",
            String.class);

        assertThat(ids).containsExactlyInAnyOrder(
            "P_PERF_METRIC_EXEC",    // 指标执行（V1.1，V1_2_4 已激活）
            "P_PERF_METRIC_TRIAL",   // 指标试算（V1.1，V1_2_4 已激活）
            "P_PERF_IMPORT_UPLOAD",  // 数据导入（V1.1，V1_1_1 已激活）
            "P_PERF_ALLOC_ADJ_ADD",  // 分配调整申请（V1.2，V1_2_4 已激活）
            "P_PERF_KPI_TRIGGER",    // KPI 计算触发（V1.1，V1_2_4 已激活）
            "P_PERF_KPI_RECALC",     // KPI 回算（V1.2，V1_2_4 已激活）
            "P_PERF_DTASK_STATUS",   // 外部任务状态上报（V1.1，V1_2_4 已激活）
            "P_PERF_SC_ROLLBACK",    // 版本回滚（V1.2，V1_2_4 已激活）
            "P_PERF_EXPORT_KPI",     // KPI 导出（V1.2，V1_2_2 已激活）
            "P_PERF_EXPORT_ALLOC"    // 分配导出（V1.2，V1_2_2 已激活）
        );
    }

    /**
     * 验证 V1.0 基线 35 条资源始终启用.
     *
     * <p>V1_0_1 脚本登记的 V1.0 资源清单（严格白名单）在任何后续迁移后
     * 都应保持 STATUS=0（启用）。
     */
    @Test
    void existingV10Resources_remainEnabled() {
        Long v10EnabledCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM PT_RESOURCE " +
            "WHERE SYS_CODE='PERF' AND STATUS = 0 " +
            "AND RESOURCE_ID IN (" +
            // V1_0_1 权威 35 条清单，详见 performance-engine-center/CLAUDE.md §7.1.1 对照表
            "  'P_PERF_METRIC_LIST','P_PERF_METRIC_GET','P_PERF_METRIC_ADD'," +
            "  'P_PERF_METRIC_UPD','P_PERF_METRIC_DEL','P_PERF_METRIC_STAT'," +
            "  'P_PERF_METRIC_REFS','P_PERF_METRIC_RBY','P_PERF_METRIC_SLOT'," +
            "  'P_PERF_METRIC_SREL'," +
            "  'P_PERF_KPI_LIST','P_PERF_KPI_GET','P_PERF_KPI_ADD'," +
            "  'P_PERF_KPI_UPD','P_PERF_KPI_DEL','P_PERF_KPI_PUB'," +
            "  'P_PERF_KPI_IADD','P_PERF_KPI_IUPD','P_PERF_KPI_IDEL'," +
            "  'P_PERF_TGT_P_LIST','P_PERF_TGT_P_GET','P_PERF_TGT_P_ADD','P_PERF_TGT_P_UPD'," +
            "  'P_PERF_TGT_V_LIST','P_PERF_TGT_V_ADD','P_PERF_TGT_V_BAT'," +
            "  'P_PERF_ALLOC_CUR','P_PERF_ALLOC_HIS','P_PERF_ALLOC_SUM'," +
            "  'P_PERF_RT_LIST','P_PERF_RT_GET'," +
            "  'P_PERF_SC_GET','P_PERF_SC_HIS','P_PERF_SC_INIT','P_PERF_SC_SW'" +
            ")",
            Long.class);
        assertThat(v10EnabledCount).isEqualTo(35L);
    }
}
