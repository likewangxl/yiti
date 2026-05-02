package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_3_0 Flyway 迁移集成测试（V1.3 Task R0.2）.
 *
 * <p>背景：V1.1 Q6 {@code DataTaskService} 以 Redis SETNX 实现幂等，但若 Redis 宕机，
 * 两个并发线程可能同时 DB 查重为 null 后双写 {@code perf_run_task}，形成同 task_key 的
 * 重复行。V1_3_0 对 {@code perf_run_task.task_key} 增加 UNIQUE KEY，作为 Redis 宕机
 * 时的 DB 兜底；配合 {@code DataTaskService} 捕获 {@code DuplicateKeyException} 降级
 * 读取既有行即可幂等返回。
 *
 * <p><strong>生产部署前置运维清单（脚本头部完整保留）</strong>：
 * <ol>
 *   <li>必须先检查 {@code SELECT task_key, COUNT(*) FROM PERF_RUN_TASK WHERE task_key IS NOT NULL GROUP BY task_key HAVING COUNT(*) > 1;}</li>
 *   <li>若有重复 → 先按业务规则清理（保留最早/最晚一条，DELETE 其余）</li>
 *   <li>然后再执行 Flyway migrate</li>
 * </ol>
 *
 * <p><strong>TDD 节奏</strong>：
 * <ul>
 *   <li>Red（R0.2 Step 1）：脚本不存在 → 3 项断言失败</li>
 *   <li>Green（R0.2 Step 3）：V1_3_0__perf_run_task_uk.sql 创建后全部通过</li>
 * </ul>
 *
 * <p>测试环境：本地 MySQL onepl_test_bootstrap（Docker 不可用，降级）。
 */
class V1_3_0FlywayIT extends PerformanceFlywayTestBase {

    @Autowired
    private ResourceLoader resourceLoader;

    /**
     * V1_3_0 脚本必须存在于 classpath:sql/ 目录下（物理文件守护）.
     */
    @Test
    void v130ScriptFile_existsInClasspath() {
        Resource r = resourceLoader.getResource("classpath:sql/V1_3_0__perf_run_task_uk.sql");
        assertThat(r.exists())
            .as("V1_3_0__perf_run_task_uk.sql 必须存在于 classpath:sql/ 下")
            .isTrue();
    }

    /**
     * V1_3_0 必须在 flyway_schema_history 中登记成功.
     */
    @Test
    void v130Script_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.3.0' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * {@code perf_run_task} 必须有 {@code uk_task_key} 唯一键，列 = task_key.
     *
     * <p>Red 阶段：脚本不存在 → 查不到 uk_task_key 索引 → 断言失败.
     * <p>Green 阶段：ALTER TABLE 成功执行后 → INDEX_NAME='uk_task_key' 列存在.
     */
    @Test
    void ukTaskKey_exists_onPerfRunTask() {
        String ukColumns = jdbc.queryForObject(
            "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) " +
            "FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='PERF_RUN_TASK' " +
            "AND INDEX_NAME='uk_task_key'",
            String.class);
        assertThat(ukColumns).as("uk_task_key 必须存在且单列为 task_key").isEqualTo("task_key");
    }

    /**
     * uk_task_key 必须是唯一索引（NON_UNIQUE=0）.
     */
    @Test
    void ukTaskKey_isUniqueIndex() {
        Integer nonUnique = jdbc.queryForObject(
            "SELECT DISTINCT NON_UNIQUE FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='PERF_RUN_TASK' " +
            "AND INDEX_NAME='uk_task_key'",
            Integer.class);
        assertThat(nonUnique).as("uk_task_key 应为唯一索引 (NON_UNIQUE=0)").isZero();
    }
}
