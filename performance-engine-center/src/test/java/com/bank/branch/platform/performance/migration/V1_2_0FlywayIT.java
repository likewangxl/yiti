package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_2_0 Flyway 迁移集成测试.
 *
 * <p>测试环境：本地 MySQL onepl_test_v103（Docker 不可用，降级）。
 *
 * <p>验证 V1_2_0__perf_v12_adjust_tables.sql 脚本在全量迁移后正确建立 V1.2 调整申请 3 张表：
 * <ul>
 *     <li>{@code perf_alloc_adjust_apply}（分配关系调整申请主单）</li>
 *     <li>{@code perf_alloc_adjust_item}（分配关系调整明细）</li>
 *     <li>{@code perf_target_adjust_apply}（目标修正申请）</li>
 * </ul>
 *
 * <p>断言内容：
 * <ol>
 *     <li>3 张表存在</li>
 *     <li>主键统一为 id (varchar(32))（V1.0 整改决策）</li>
 *     <li>分配主单 uk_apply_no 唯一键存在</li>
 *     <li>分配明细 uk_apply_emp 唯一键存在（同一申请内员工唯一）</li>
 *     <li>目标主单 idx_plan_id 索引存在（按计划查询）</li>
 * </ol>
 */
class V1_2_0FlywayIT extends PerformanceFlywayTestBase {

    /**
     * 3 张调整申请表必须在迁移后存在.
     */
    @Test
    void v12AdjustTables_created() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema=DATABASE() " +
            "AND table_name IN (" +
            "  'perf_alloc_adjust_apply'," +
            "  'perf_alloc_adjust_item'," +
            "  'perf_target_adjust_apply'" +
            ")",
            Integer.class);
        assertThat(count).isEqualTo(3);
    }

    /**
     * perf_alloc_adjust_apply 主键列必须是 id (varchar(32))，与生产 DDL 对齐.
     */
    @Test
    void allocAdjustApply_primaryKey_isIdVarchar32() {
        String pkColumn = jdbc.queryForObject(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_alloc_adjust_apply' " +
            "AND COLUMN_KEY='PRI'",
            String.class);
        assertThat(pkColumn).isEqualTo("id");

        String dataType = jdbc.queryForObject(
            "SELECT DATA_TYPE FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_alloc_adjust_apply' " +
            "AND COLUMN_NAME='id'",
            String.class);
        assertThat(dataType).isEqualTo("varchar");

        Long charMaxLen = jdbc.queryForObject(
            "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_alloc_adjust_apply' " +
            "AND COLUMN_NAME='id'",
            Long.class);
        assertThat(charMaxLen).isEqualTo(32L);
    }

    /**
     * perf_alloc_adjust_apply 必须有 uk_apply_no 唯一键（申请号唯一）.
     */
    @Test
    void allocAdjustApply_uniqueKey_applyNo_exists() {
        List<String> cols = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_alloc_adjust_apply' " +
            "AND INDEX_NAME='uk_apply_no' ORDER BY SEQ_IN_INDEX",
            String.class);
        assertThat(cols).contains("apply_no");

        Integer nonUnique = jdbc.queryForObject(
            "SELECT DISTINCT NON_UNIQUE FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_alloc_adjust_apply' " +
            "AND INDEX_NAME='uk_apply_no'",
            Integer.class);
        assertThat(nonUnique).isZero(); // 0 表示唯一索引
    }

    /**
     * perf_alloc_adjust_item 必须有 uk_apply_emp 唯一键（同一申请内员工唯一）.
     */
    @Test
    void allocAdjustItem_uniqueKey_applyEmp_exists() {
        List<String> cols = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_alloc_adjust_item' " +
            "AND INDEX_NAME='uk_apply_emp' ORDER BY SEQ_IN_INDEX",
            String.class);
        assertThat(cols).containsExactly("apply_id", "emp_id");
    }

    /**
     * perf_target_adjust_apply 必须有 idx_plan_id 索引（按目标方案查询）.
     */
    @Test
    void targetAdjustApply_hasPlanIdIndex() {
        List<String> cols = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='perf_target_adjust_apply' " +
            "AND INDEX_NAME='idx_plan_id'",
            String.class);
        assertThat(cols).contains("plan_id");
    }

    /**
     * V1_2_0 脚本必须在 flyway_schema_history 中登记成功执行.
     *
     * <p>这是 V1.2 基线版本号标识：即使生产 DDL 已存在 3 张表（通过 V1_0_0 基线脚本建立），
     * 本模块仍需通过 V1_2_0 显式占位登记，表示 V1.2 迁移链条已就位，后续 V1_2_1/V1_2_2
     * 脚本可在此基础上叠加.
     *
     * <p>Red 阶段：V1_2_0 脚本不存在，flyway_schema_history 无此版本号记录；此断言必失败.
     * <p>Green 阶段：V1_2_0__perf_v12_adjust_tables.sql 存在并执行成功后通过.
     */
    @Test
    void v12BaselineScript_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history " +
            "WHERE version='1.2.0' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
