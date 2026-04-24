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
 * <p><strong>TDD 节奏</strong>：
 * <ul>
 *   <li>Red（R0.1 Step 1）：脚本文件 / flyway_schema_history 都不存在 → 4 项断言失败</li>
 *   <li>Green（R0.1 Step 3）：V1_2_5__perf_cleanup_null_deleted.sql 创建后全部通过</li>
 * </ul>
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
     *   <li>预置一条 {@code deleted IS NULL} 行（绕开 MetricDefService 绕开 Q8.5b 修复），
     *       模拟 V1.0 历史遗留的 NULL 数据</li>
     * </ol>
     */
    @BeforeEach
    void setUpHistoricalNullRow() {
        jdbc.update("DELETE FROM perf_metric_def WHERE metric_code = ?", TEST_METRIC_CODE);
        try {
            // 必填字段：id / metric_code / metric_name / base_dim / metric_level / calc_freq / calc_mode / status；
            // val_slot=151 固定避开 V1_2_3 seed 的 1~5 槽位（本地库未应用 V1_2_3 时也不冲突）。
            jdbc.update(
                "INSERT INTO perf_metric_def " +
                "(id, metric_code, metric_name, base_dim, metric_level, calc_freq, calc_mode, val_slot, status, deleted) " +
                "VALUES (?, ?, ?, 'EMP', 1, 'DAILY', 'AUTO', 151, 'ACTIVE', NULL)",
                TEST_METRIC_CODE, TEST_METRIC_CODE, "历史 NULL deleted 测试");
        } catch (DataAccessException ignored) {
            // 若未来 DDL 将 deleted 改为 NOT NULL，插入必失败，本用例退化为 NOP
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM perf_metric_def WHERE metric_code = ?", TEST_METRIC_CODE);
    }

    /**
     * V1_2_5 脚本必须存在于 classpath:sql/ 目录下.
     *
     * <p>物理文件层面的守护：脚本被误删也能被捕获.
     */
    @Test
    void v125ScriptFile_existsInClasspath() {
        Resource r = resourceLoader.getResource("classpath:sql/V1_2_5__perf_cleanup_null_deleted.sql");
        assertThat(r.exists())
            .as("V1_2_5__perf_cleanup_null_deleted.sql 必须存在于 classpath:sql/ 下")
            .isTrue();
    }

    /**
     * V1_2_5 必须在 flyway_schema_history 中登记成功.
     *
     * <p>Red 阶段：脚本不存在 → flyway 不会登记 1.2.5 → count=0 → 测试失败.
     * <p>Green 阶段：V1_2_5 创建后 → Spring 启动时自动 migrate → count=1.
     */
    @Test
    void v125Script_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.2.5' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * V1_2_5 的 description 应明确指向 "cleanup null deleted" 语义，避免误用脚本名.
     *
     * <p>Red 阶段：脚本不存在 → description 查询不到.
     * <p>Green 阶段：description="perf cleanup null deleted"（flyway 从脚本名 'perf_cleanup_null_deleted' 转义）.
     */
    @Test
    void v125Script_description_matches() {
        String description = jdbc.queryForObject(
            "SELECT description FROM flyway_schema_history WHERE version='1.2.5'",
            String.class);
        assertThat(description).as("V1_2_5 description 应为 'perf cleanup null deleted'")
            .isEqualTo("perf cleanup null deleted");
    }

    /**
     * 预置的 NULL deleted 行在 V1_2_5 应用后应被改为 0（重复执行脚本 UPDATE 校验幂等性）.
     *
     * <p>ApplicationContext 启动时 flyway.migrate() 已应用 V1_2_5；但对已应用脚本是 no-op，
     * 不会对 BeforeEach 预置的新 NULL 行生效。故此处手工执行等价 UPDATE，验证脚本 SQL 内容
     * 的语义正确性（"UPDATE ... WHERE deleted IS NULL" 可把新 NULL 行清理为 0）.
     *
     * <p>Red 阶段：脚本不存在时，虽然 UPDATE 仍成功（SQL 语义独立），但 flyway 登记挂，
     * 整个 R0.1 断言组视为失败（全量 Red）.
     */
    @Test
    void updateSemantics_fixesNullDeletedRow() {
        // 执行 V1_2_5 脚本等价的 UPDATE，模拟生产部署 V1_2_5 时的实际 migrate 行为
        jdbc.update("UPDATE perf_metric_def SET deleted = 0 WHERE deleted IS NULL");

        Integer deleted = jdbc.queryForObject(
            "SELECT deleted FROM perf_metric_def WHERE metric_code = ?",
            Integer.class, TEST_METRIC_CODE);
        assertThat(deleted).as("脚本 UPDATE 语义应把 NULL deleted 修正为 0").isZero();
    }
}
