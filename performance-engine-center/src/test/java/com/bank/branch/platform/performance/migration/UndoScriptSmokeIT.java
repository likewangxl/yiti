package com.bank.branch.platform.performance.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Undo 反向脚本 Smoke 测试（Task B8）.
 *
 * <p>背景：项目使用 Flyway Community Edition（无官方 undo 支持）。
 * undo 脚本作为应急运维手册，DBA 在紧急情况下手动执行以还原 DDL 状态。
 * 本测试仅验证脚本语法可被 MySQL 解析并执行，不验证业务语义回滚的完整性。
 *
 * <p>@BeforeEach 保证数据库处于 V1_0_3 状态：
 * 先执行 undo 还原到 V1_0_2（幂等），再修复 Flyway 历史，再 migrate 重新应用 V1_0_3。
 */
class UndoScriptSmokeIT extends PerformanceFlywayTestBase {

    @Autowired
    private Flyway flyway;

    /**
     * 每个测试方法执行前，确保数据库处于 V1_0_3 状态。
     * 流程：执行 undo 还原（幂等）→ 修复 Flyway 历史 → 重新迁移至 V1_0_3。
     */
    @BeforeEach
    void ensureV103State() {
        // 1. 先执行 V1_0_3 undo（幂等：如果结构已不存在则忽略错误）
        String undoPath = "src/main/resources/undo-scripts/V1_0_3__undo.sql";
        ResourceDatabasePopulator undo = new ResourceDatabasePopulator();
        undo.addScript(new FileSystemResource(Paths.get(undoPath).toAbsolutePath().toFile()));
        undo.setSeparator(";");
        undo.setIgnoreFailedDrops(true);
        try {
            undo.execute(jdbc.getDataSource());
        } catch (Exception ignored) {
            // 若字段已不存在（已在 V1_0_2 状态），忽略错误
        }

        // 2. 删除 flyway_schema_history 中 V1_0_3 记录（若存在），允许重新迁移
        try {
            jdbc.execute("DELETE FROM flyway_schema_history WHERE version = '1.0.3'");
        } catch (Exception ignored) {
            // 历史表可能不存在
        }

        // 3. repair 清理任何 FAILED 标记后再 migrate，重新应用 V1_0_3
        flyway.repair();
        flyway.migrate();
    }

    @Test
    void undoV1_0_3_revertsDdlChanges() throws Exception {
        // 验证当前 uk_scope_dim_date_version 存在（Flyway 已迁移到 V1_0_3）
        Integer ukBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND INDEX_NAME='uk_scope_dim_date_version'", Integer.class);
        assertThat(ukBefore).isGreaterThan(0);

        // 执行 V1_0_3 undo 脚本（多语句）
        String undoPath = "src/main/resources/undo-scripts/V1_0_3__undo.sql";
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new FileSystemResource(Paths.get(undoPath).toAbsolutePath().toFile()));
        populator.setSeparator(";");
        populator.setIgnoreFailedDrops(false);
        populator.execute(jdbc.getDataSource());

        // 验证：uk_scope_dim_date_version 不再存在，已还原为 uk_scope_dim_date
        Integer ukAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND INDEX_NAME='uk_scope_dim_date_version'", Integer.class);
        assertThat(ukAfter).isZero();

        // 验证：sys_control 新增字段已删除
        Integer fieldCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND COLUMN_NAME IN ('remark','updated_by','publish_source','publish_by','publish_time')",
            Integer.class);
        assertThat(fieldCnt).isZero();

        // 验证：perf_metric_def 槽位唯一键已删除
        Integer slotUkAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_metric_def' " +
            "AND INDEX_NAME='uk_base_dim_slot_alive'", Integer.class);
        assertThat(slotUkAfter).isZero();

        // 验证：perf_metric_def 新增字段已删除
        Integer metricFieldCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_metric_def' " +
            "AND COLUMN_NAME IN ('unit','decimal_places','deleted','description')",
            Integer.class);
        assertThat(metricFieldCnt).isZero();
    }

    @Test
    void undoV1_0_4_revertsResourceCleanup() throws Exception {
        // V1_0_4 是 Phase E 交付，此处 undo 脚本为占位（UPDATE pt_resource 操作）
        // 只验证脚本语法可执行，不验证语义（因为测试库中 pt_resource 状态可能与预期不同）
        String undoPath = "src/main/resources/undo-scripts/V1_0_4__undo.sql";
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new FileSystemResource(Paths.get(undoPath).toAbsolutePath().toFile()));
        populator.setSeparator(";");
        populator.setIgnoreFailedDrops(true); // V1_0_4 可能 UPDATE 0 行，不算失败
        populator.execute(jdbc.getDataSource());
        // 执行无异常即认为 smoke 通过
    }
}
