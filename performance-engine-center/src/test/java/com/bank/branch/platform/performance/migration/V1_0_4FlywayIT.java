package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_0_4 Flyway 迁移集成测试.
 *
 * <p>验证 V1_0_4 脚本将 V1.1/V1.2 规划但尚未实现的 10 条 PT_RESOURCE 记录
 * 注册并标记为禁用状态（STATUS=1），避免 V1.0 对外误授权这些未实现端点。
 *
 * <p>测试环境：本地 MySQL onepl_test_v103（Docker 不可用，降级）。
 */
class V1_0_4FlywayIT extends PerformanceFlywayTestBase {

    /**
     * 验证 10 条 V1.1/V1.2 规划资源已注册并设置为禁用状态（STATUS=1）.
     *
     * <p>这些端点对应 V1.1/V1.2 功能：指标执行、试算、数据导入、分配调整、
     * KPI 计算触发、回算、外部上报、版本回滚、KPI 导出、分配导出。
     * V1.0 不实现，注册为禁用状态防止误授权；V1.1/V1.2 上线时通过新脚本激活。
     */
    @Test
    void pendingResources_haveStatusDisabled() {
        // 查询已标记为禁用的 V1.1/V1.2 规划资源
        List<String> ids = jdbc.queryForList(
            "SELECT RESOURCE_ID FROM pt_resource " +
            "WHERE RESOURCE_ID IN (" +
            "  'P_PERF_METRIC_EXEC','P_PERF_METRIC_TRIAL'," +
            "  'P_PERF_IMPORT_UPLOAD','P_PERF_ALLOC_ADJ_ADD'," +
            "  'P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC'," +
            "  'P_PERF_DTASK_STATUS','P_PERF_SC_ROLLBACK'," +
            "  'P_PERF_EXPORT_KPI','P_PERF_EXPORT_ALLOC'" +
            ") AND STATUS = 1 " +
            "ORDER BY RESOURCE_ID",
            String.class);

        // 全部 10 条 V1.1/V1.2 未实现端点应以禁用状态注册
        assertThat(ids).containsExactlyInAnyOrder(
            "P_PERF_METRIC_EXEC",    // 指标执行（V1.1）
            "P_PERF_METRIC_TRIAL",   // 指标试算（V1.1）
            "P_PERF_IMPORT_UPLOAD",  // 数据导入（V1.1）
            "P_PERF_ALLOC_ADJ_ADD",  // 分配调整申请（V1.2）
            "P_PERF_KPI_TRIGGER",    // KPI 计算触发（V1.1）
            "P_PERF_KPI_RECALC",     // KPI 回算（V1.2）
            "P_PERF_DTASK_STATUS",   // 外部任务状态上报（V1.1）
            "P_PERF_SC_ROLLBACK",    // 版本回滚（V1.2）
            "P_PERF_EXPORT_KPI",     // KPI 导出（V1.2）
            "P_PERF_EXPORT_ALLOC"    // 分配导出（V1.2）
        );
    }

    /**
     * 验证 V1.0 已实现的 35 条资源保持启用状态（STATUS=0）不受影响.
     */
    @Test
    void existingV10Resources_remainEnabled() {
        Long disabledV10Count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pt_resource " +
            "WHERE SYS_CODE='PERF' AND STATUS != 1 " +
            "AND RESOURCE_ID NOT IN (" +
            "  'P_PERF_METRIC_EXEC','P_PERF_METRIC_TRIAL'," +
            "  'P_PERF_IMPORT_UPLOAD','P_PERF_ALLOC_ADJ_ADD'," +
            "  'P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC'," +
            "  'P_PERF_DTASK_STATUS','P_PERF_SC_ROLLBACK'," +
            "  'P_PERF_EXPORT_KPI','P_PERF_EXPORT_ALLOC'" +
            ")",
            Long.class);
        // V1.0 的 35 条资源应全部保持 STATUS=0（启用）
        assertThat(disabledV10Count).isEqualTo(35L);
    }
}
