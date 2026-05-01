package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_7_0 Flyway 迁移集成测试（V1.7 指标级调度改造 P0）.
 *
 * <p>V1_7_0 脚本为 PERF_METRIC_DEF 表补充 3 个调度相关字段：
 * <ul>
 *   <li>cron_expr     — 自定义 cron；留空按 calc_freq 推导默认</li>
 *   <li>subject_sql   — EXPR/GROOVY 类型主体集合 SQL</li>
 *   <li>last_run_time — 最近一次自动调度执行时间</li>
 * </ul>
 * 同时新增 idx_metric_def_schedulable 联合索引（status, calc_mode, deleted）。
 *
 * <p><strong>TDD 节奏</strong>：
 * <ul>
 *   <li>Red（P0 Step 1）：脚本不存在 → 2 项断言失败</li>
 *   <li>Green（P0 Step 3）：V1_7_0__perf_metric_def_schedule_cols.sql 创建后全部通过</li>
 * </ul>
 *
 * <p>测试环境：本地 MySQL yiti（application-test.yml 配置，Flyway 按 classpath:sql 迁移）。
 */
@SpringBootTest(classes = {com.bank.branch.platform.performance.support.PerfTestApp.class,
                            com.bank.branch.platform.performance.support.PerfTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:sql",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.baseline-version=0",
    "spring.flyway.clean-disabled=false",
    "spring.flyway.validate-on-migrate=false",
    "spring.flyway.ignore-migration-patterns=*:missing,*:pending,*:ignored,*:future"
})
class V1_7_0FlywayIT {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * V1_7_0 必须为 PERF_METRIC_DEF 补齐 cron_expr / subject_sql / last_run_time 3 列.
     *
     * <p>Red 阶段：脚本不存在 → 列不存在 → COUNT=0 → 断言失败.
     * <p>Green 阶段：ALTER TABLE 应用后 → COUNT=3.
     */
    @Test
    void v1_7_0_adds_three_columns_to_perf_metric_def() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_METRIC_DEF' " +
            "AND COLUMN_NAME IN ('cron_expr','subject_sql','last_run_time')",
            Integer.class);
        assertThat(cnt).isEqualTo(3);
    }

    /**
     * V1_7_0 必须在 PERF_METRIC_DEF 上创建 idx_metric_def_schedulable 索引.
     *
     * <p>Red 阶段：脚本不存在 → 索引不存在 → COUNT=0 → 断言失败.
     * <p>Green 阶段：CREATE INDEX 应用后 → COUNT 大于 0.
     */
    @Test
    void v1_7_0_creates_idx_metric_def_schedulable() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_METRIC_DEF' " +
            "AND INDEX_NAME = 'idx_metric_def_schedulable'",
            Integer.class);
        assertThat(cnt).isGreaterThan(0);
    }
}
