package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_2_5 Flyway 迁移集成测试（V1.3 Task R0.1）.
 *
 * <p>背景：V1.0 {@code MetricDefService.create} 未初始化 {@code deleted} 字段
 * （V1.2 Q8.5b 修复），生产可能已有 {@code deleted IS NULL} 的历史孤儿行；
 * 这些行在 {@code selectByMetricCode(WHERE deleted=0)} 下不可见。
 *
 * <p>V1_2_5 脚本幂等 UPDATE 把所有 NULL deleted 行改为 0，修复历史数据。
 *
 * <p>Red 阶段（R0.1 Step 1）：脚本不存在 → flyway_schema_history 无 1.2.5 记录
 * + 预置的 NULL 行仍保持 NULL。
 *
 * <p>Green 阶段（R0.1 Step 3）：V1_2_5__perf_cleanup_null_deleted.sql 存在后
 * flyway.migrate() 会把 NULL 清理为 0，测试通过。
 *
 * <p>测试环境：本地 MySQL onepl_test_v103（Docker 不可用，降级）。
 */
class V1_2_5FlywayIT extends PerformanceFlywayTestBase {

    /** 测试专用的 NULL deleted 历史行 metric_code / id（唯一前缀便于清理）. */
    private static final String TEST_METRIC_CODE = "TEST_NULL_V125";

    @Autowired
    private ResourceLoader resourceLoader;

    /**
     * 每个用例前：
     * <ol>
     *   <li>删除测试专用的历史行（保证用例幂等）</li>
     *   <li>预置一条 {@code deleted IS NULL} 行（绕开 MetricDefService，避免受 Q8.5b 修复影响）</li>
     * </ol>
     *
     * <p>为兼容 V1_0_3 后续追加的 NOT NULL / UK 约束，先检测 {@code deleted} 列当前允许 NULL 才插入；
     * 若列已被 DDL 改为 NOT NULL，直接跳过（此时 V1_2_5 清理已天然无意义）。
     */
    @BeforeEach
    void setUpHistoricalNullRow() {
        jdbc.update("DELETE FROM perf_metric_def WHERE metric_code = ?", TEST_METRIC_CODE);
        // 生产 DDL 未对 deleted 列加 NOT NULL（V1_0_0 原始 DDL：DEFAULT 0，允许 NULL），
        // 本处直接绕开 Service 层，模拟 V1.0 bug 期间的 "INSERT 未显式写 deleted" 产物行.
        try {
            // 必填字段：id / metric_code / metric_name / base_dim / metric_level / calc_freq / calc_mode / status；
            // val_slot=151 固定避开 V1_2_3 seed 的 1~5 槽位（本地库未应用 V1_2_3 时也不冲突）。
            jdbc.update(
                "INSERT INTO perf_metric_def " +
                "(id, metric_code, metric_name, base_dim, metric_level, calc_freq, calc_mode, val_slot, status, deleted) " +
                "VALUES (?, ?, ?, 'EMP', 1, 'DAILY', 'AUTO', 151, 'ACTIVE', NULL)",
                TEST_METRIC_CODE, TEST_METRIC_CODE, "历史 NULL deleted 测试");
        } catch (DataAccessException ignored) {
            // 若 DDL 已把 deleted 改为 NOT NULL，插入必失败，本用例退化为 NOP（V1_2_5 也无需生效）
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM perf_metric_def WHERE metric_code = ?", TEST_METRIC_CODE);
    }

    /**
     * V1_2_5 必须在 flyway_schema_history 中登记成功.
     *
     * <p>Red 阶段：脚本不存在 → flyway 不会登记 1.2.5 → count=0 → 测试失败.
     * <p>Green 阶段：V1_2_5__perf_cleanup_null_deleted.sql 存在 → 登记成功.
     */
    @Test
    void v125Script_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.2.5' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * 迁移完成后，预置的 NULL deleted 历史行应被修正为 0.
     *
     * <p>Red 阶段：脚本未提供 → 预置行 deleted 仍为 NULL → 断言失败.
     * <p>Green 阶段：V1_2_5 UPDATE 命中预置行 → deleted=0.
     */
    @Test
    void migration_fixesNullDeleted() {
        Integer deleted = jdbc.queryForObject(
            "SELECT deleted FROM perf_metric_def WHERE metric_code = ?",
            Integer.class, TEST_METRIC_CODE);
        assertThat(deleted).as("V1_2_5 必须把 NULL deleted 修正为 0").isZero();
    }

    /**
     * 迁移后，库中不应再存在任何 {@code deleted IS NULL} 行（除了 BeforeEach 预置后、
     * flyway 已 migrate 的幂等状态下）.
     *
     * <p>该断言覆盖"库中任意 NULL 行"而非仅测试行，兜底"生产历史 NULL" 被清理到位.
     */
    @Test
    void migration_noNullDeletedRowsRemain() {
        Long nullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM perf_metric_def WHERE deleted IS NULL", Long.class);
        assertThat(nullCount).as("V1_2_5 清理后不应有任何 deleted IS NULL 行").isZero();
    }

    /**
     * V1_2_5 脚本必须存在于 classpath:sql/ 目录下，为生产部署保留运维评审入口.
     *
     * <p>该断言是"物理文件层面"的守护：脚本被误删除时也能被测试捕获，与 flyway_schema_history
     * 断言形成互补.
     */
    @Test
    void v125ScriptFile_existsInClasspath() {
        Resource r = resourceLoader.getResource("classpath:sql/V1_2_5__perf_cleanup_null_deleted.sql");
        assertThat(r.exists())
            .as("V1_2_5__perf_cleanup_null_deleted.sql 必须存在于 classpath:sql/ 下")
            .isTrue();
    }
}
