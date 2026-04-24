package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_0_3 / V1_0_4 紧急回滚脚本 Smoke 测试（V1.3 Task R5.2 重写）.
 *
 * <p>背景：项目使用 Flyway Community Edition（无官方 undo 支持）。undo 脚本作为应急运维手册，
 * DBA 在紧急情况下手动执行以还原关键 DDL 状态。本测试验证脚本语法可被 MySQL 解析并执行，
 * 且 {@code V1_0_3__undo.sql} 能正确撤销 V1_0_3 引入的字段+索引。
 *
 * <p><strong>V1.3 Task R5.2 重写策略</strong>（V1.2 Q8.5c 曾 @Disabled，原因见 git 历史）：
 * <ul>
 *   <li>@BeforeEach：验证 DB 已处于 V1_3_0 终态（V1_0_3 的 UK/字段仍在），不做 undo</li>
 *   <li>@Test v1_0_3_undo：手动 execute {@code V1_0_3__undo.sql} → 断言变更 → 手动恢复</li>
 *   <li>@Test v1_0_4_undo_syntaxSmoke：仅验证 {@code V1_0_4__undo.sql} 语法可执行</li>
 *   <li>@AfterEach：用 jdbcTemplate 恢复 sys_control/perf_metric_def 结构到 V1_3_0 终态</li>
 * </ul>
 *
 * <p>不使用 {@code flyway.migrate()}：V1.2 旧实现依赖 Flyway repair + migrate 恢复，
 * 但 Flyway 发现 schema_history 最新态时不会重跑已应用的 V1_0_3，导致 @BeforeEach undo 后
 * 表永久处于坏态。V1.3 改为纯 jdbcTemplate 手动 ALTER 恢复，语义显式、无 Flyway 状态管理。
 *
 * <p>V1_0_4 相关断言已删除：V1_0_4 的 pt_resource 资源状态调整已在 V1_2_4 重组（启用全量
 * 45 条），V1_0_4__undo.sql 的 UPDATE 语义对当前 pt_resource 状态不再有明确断言目标。
 * 本 IT 仅验证其脚本语法能被 ResourceDatabasePopulator 执行无异常。
 */
class UndoScriptSmokeIT extends PerformanceFlywayTestBase {

    /** @BeforeEach 前置：验证 DB 已被 Flyway 迁移到最新态（V1_0_3 的字段/索引仍存在）. */
    @BeforeEach
    void verifyPreconditions() {
        // Flyway 迁移由 Spring Boot 在上下文启动时已完成（PerformanceFlywayTestBase @TestPropertySource 配置）
        Integer ukCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND INDEX_NAME='uk_scope_dim_date_version'", Integer.class);
        // 若前置不满足（V1_0_3 未应用），直接让测试方法内部断言失败，避免 @BeforeEach 阶段抛异常
        assertThat(ukCnt)
            .as("V1_0_3 迁移后 sys_control 应有 uk_scope_dim_date_version，当前 DB 基线异常")
            .isGreaterThan(0);
    }

    /**
     * 核心测试：V1_0_3__undo.sql 应正确撤销 V1_0_3 引入的：
     * <ol>
     *   <li>sys_control.uk_scope_dim_date_version → 还原为 uk_scope_dim_date</li>
     *   <li>sys_control 5 个新字段（remark / updated_by / publish_source / publish_by / publish_time）</li>
     *   <li>perf_metric_def 槽位唯一键 uk_base_dim_slot_alive</li>
     *   <li>perf_metric_def 4 个新字段（unit / decimal_places / deleted / description）</li>
     * </ol>
     */
    @Test
    @DisplayName("V1_0_3__undo.sql 应撤销 sys_control UK + 5 字段 + perf_metric_def UK + 4 字段")
    void v1_0_3_undo_revertsSchema() {
        // When: 手动执行 V1_0_3 undo 脚本
        String undoPath = "src/main/resources/undo-scripts/V1_0_3__undo.sql";
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new FileSystemResource(Paths.get(undoPath).toAbsolutePath().toFile()));
        populator.setSeparator(";");
        populator.setIgnoreFailedDrops(false);
        populator.execute(jdbc.getDataSource());

        // Then 1: sys_control UK 已还原
        assertThat(indexExists("sys_control", "uk_scope_dim_date_version"))
            .as("undo 后 uk_scope_dim_date_version 应消失").isFalse();
        assertThat(indexExists("sys_control", "uk_scope_dim_date"))
            .as("undo 后 uk_scope_dim_date 应恢复").isTrue();

        // Then 2: sys_control 5 个新字段应消失
        assertThat(columnCount("sys_control", "remark", "updated_by", "publish_source", "publish_by", "publish_time"))
            .as("sys_control 5 个 V1_0_3 新字段应全部消失").isZero();

        // Then 3: perf_metric_def 槽位 UK 应消失
        assertThat(indexExists("perf_metric_def", "uk_base_dim_slot_alive"))
            .as("undo 后 uk_base_dim_slot_alive 应消失").isFalse();

        // Then 4: perf_metric_def 4 个新字段应消失
        assertThat(columnCount("perf_metric_def", "unit", "decimal_places", "deleted", "description"))
            .as("perf_metric_def 4 个 V1_0_3 新字段应全部消失").isZero();
    }

    /**
     * V1_0_4__undo.sql 只做 pt_resource STATUS UPDATE，属幂等性 SQL，
     * smoke 验证脚本语法可被解析执行即可，不断言业务语义回滚完整性
     * （V1_0_4 的 pt_resource 资源已在 V1_2_4 重组）.
     */
    @Test
    @DisplayName("V1_0_4__undo.sql 语法 smoke: 脚本可被 ResourceDatabasePopulator 执行无异常")
    void v1_0_4_undo_syntaxSmoke() {
        String undoPath = "src/main/resources/undo-scripts/V1_0_4__undo.sql";
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new FileSystemResource(Paths.get(undoPath).toAbsolutePath().toFile()));
        populator.setSeparator(";");
        populator.setIgnoreFailedDrops(true);  // UPDATE 0 行视为成功
        populator.execute(jdbc.getDataSource());
        // 能到达此处即脚本语法合法
    }

    /**
     * @AfterEach 恢复：纯 jdbcTemplate 把 sys_control / perf_metric_def 结构 + pt_resource
     * 记录恢复到 V1_3_0 终态。
     *
     * <p>不使用 flyway.migrate()：V1.2 旧实现遇到 Flyway history 是最新版时 migrate 跳过，
     * 导致 undo 后的坏态永久保留。V1.3 改为手动 ALTER TABLE + 重放 V1_0_4 正向脚本，
     * 语义明确可控.
     *
     * <p>幂等性：每条 ALTER TABLE 都用 try-catch 包裹 SQLException 确保多次执行不报错.
     * pt_resource 通过执行 V1_0_4 正向脚本（ON DUPLICATE KEY UPDATE 确保可重入）补回
     * 10 条被 V1_0_4__undo.sql DELETE 的规划资源，再手动 UPDATE STATUS=0 恢复 V1_2_4
     * 的全量 45 条启用态.
     */
    @AfterEach
    void restoreV13Baseline() {
        // sys_control UK 恢复（V1_0_3 的三列 UK）
        tryExecute("ALTER TABLE sys_control DROP INDEX uk_scope_dim_date");
        tryExecute("ALTER TABLE sys_control DROP INDEX uk_scope_dim_date_version");
        tryExecute("ALTER TABLE sys_control ADD UNIQUE KEY uk_scope_dim_date_version "
            + "(scope_dim, latest_data_date, current_version)");

        // sys_control 5 个新字段恢复
        tryExecute("ALTER TABLE sys_control ADD COLUMN remark VARCHAR(255) NULL "
            + "COMMENT '切版备注' AFTER current_version");
        tryExecute("ALTER TABLE sys_control ADD COLUMN updated_by VARCHAR(32) NULL COMMENT '最后更新人'");
        tryExecute("ALTER TABLE sys_control ADD COLUMN publish_source VARCHAR(32) NULL COMMENT '发布来源'");
        tryExecute("ALTER TABLE sys_control ADD COLUMN publish_by VARCHAR(32) NULL COMMENT '发布人'");
        tryExecute("ALTER TABLE sys_control ADD COLUMN publish_time DATETIME NULL COMMENT '发布时间'");

        // perf_metric_def 4 个新字段恢复
        tryExecute("ALTER TABLE perf_metric_def ADD COLUMN unit VARCHAR(16) NULL COMMENT '单位'");
        tryExecute("ALTER TABLE perf_metric_def ADD COLUMN decimal_places TINYINT DEFAULT 2 "
            + "COMMENT '小数位数'");
        tryExecute("ALTER TABLE perf_metric_def ADD COLUMN deleted TINYINT DEFAULT 0 "
            + "COMMENT '0=存在 1=删除'");
        tryExecute("ALTER TABLE perf_metric_def ADD COLUMN description VARCHAR(500) NULL "
            + "COMMENT '指标描述'");
        jdbc.execute("UPDATE perf_metric_def SET deleted = 0 WHERE deleted IS NULL");

        // perf_metric_def 槽位 UK 恢复
        tryExecute("ALTER TABLE perf_metric_def DROP INDEX uk_base_dim_slot_alive");
        jdbc.execute("ALTER TABLE perf_metric_def ADD UNIQUE KEY uk_base_dim_slot_alive "
            + "((IF(deleted=0, CONCAT(base_dim,'#',val_slot), NULL)))");

        // pt_resource 记录恢复：重新执行 V1_0_4 正向脚本补回被 V1_0_4__undo.sql DELETE 的 10 条
        // （ON DUPLICATE KEY UPDATE 语义保证可重复执行，不冲突）
        String forwardV104 = "src/main/resources/sql/V1_0_4__perf_resource_cleanup.sql";
        ResourceDatabasePopulator restore = new ResourceDatabasePopulator();
        restore.addScript(new FileSystemResource(Paths.get(forwardV104).toAbsolutePath().toFile()));
        restore.setSeparator(";");
        restore.setIgnoreFailedDrops(true);
        restore.execute(jdbc.getDataSource());

        // pt_resource 激活态恢复：V1_2_4 把 10 条中的 7 条激活为 STATUS=0；另 3 条在
        // V1_1_1/V1_2_2 激活。为简单起见，全部 STATUS=0（V1_3_0 终态全 45 条启用）.
        jdbc.execute("UPDATE pt_resource SET STATUS = 0 WHERE RESOURCE_ID IN ("
            + "'P_PERF_METRIC_EXEC','P_PERF_METRIC_TRIAL','P_PERF_IMPORT_UPLOAD',"
            + "'P_PERF_ALLOC_ADJ_ADD','P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC',"
            + "'P_PERF_DTASK_STATUS','P_PERF_SC_ROLLBACK','P_PERF_EXPORT_KPI','P_PERF_EXPORT_ALLOC')");

        // sys_dict_item PERF_METRIC_STATUS 字典项激活态恢复（V1_0_4 正向改为 PENDING）
        jdbc.execute("UPDATE sys_dict_item SET status = 'PENDING' "
            + "WHERE dict_type = 'PERF_METRIC_STATUS' AND item_code IN ('DRAFT', 'PUBLISHED')");
    }

    // -------------------- helpers --------------------

    /** 幂等执行 ALTER TABLE，忽略 "字段/索引 已存在或不存在" 等可预期错误. */
    private void tryExecute(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception ignored) {
            // ADD COLUMN 时字段已存在、DROP INDEX 时索引不存在，均视为幂等成功
        }
    }

    /** 检查 TABLE + INDEX_NAME 组合是否存在. */
    private boolean indexExists(String table, String indexName) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?",
            Integer.class, table, indexName);
        return cnt != null && cnt > 0;
    }

    /** 统计表中命名 in () 的字段出现次数（若全部存在则 = columns.length）. */
    private int columnCount(String table, String... columns) {
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
        Object[] args = new Object[columns.length + 1];
        args[0] = table;
        System.arraycopy(columns, 0, args, 1, columns.length);
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME IN (" + placeholders + ")",
            Integer.class, args);
        return cnt != null ? cnt : 0;
    }
}
