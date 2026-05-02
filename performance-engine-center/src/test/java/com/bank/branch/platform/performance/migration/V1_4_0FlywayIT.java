package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_4_0 Flyway 迁移集成测试（V1.4 Task S2.1）.
 *
 * <p>背景：V1.3 R1.1/R1.2 为 TargetValue / TargetPlan 引入数据范围注入,
 * 但 ScopeColumns 因 DDL 缺字段全部降级到 {@code created_by}（SELF / ORG 均无精确语义）.
 *
 * <p>V1_4_0 通过 {@code ALTER TABLE ADD COLUMN owner_emp_id / owner_org_code + 回填} 把
 * perf_target_plan / perf_target_value 表的 owner 字段补齐, 为 S2.3 ScopeColumns 精化
 * （owner_emp_id / owner_org_code 替代 created_by）提供 DB 基础.
 *
 * <p><strong>生产部署前置运维清单（脚本头部完整保留）</strong>：
 * <ol>
 *   <li>小数据量（&lt; 10 万行）→ Flyway 脚本内联 UPDATE 回填 owner_emp_id = created_by</li>
 *   <li>大数据量（&gt; 100 万行）→ 运维按 runbook 改走分批存储过程 / pt-online-schema-change</li>
 *   <li>失败回滚 → undo-scripts/V1_4_0__undo.sql 撤销 ADD COLUMN + ADD INDEX + flyway_schema_history 记录</li>
 * </ol>
 *
 * <p><strong>TDD 节奏</strong>：
 * <ul>
 *   <li>Red（S2.1 Step 1）：脚本不存在 → 4 项断言失败</li>
 *   <li>Green（S2.1 Step 3）：V1_4_0__perf_target_owner_cols.sql 创建后全部通过</li>
 * </ul>
 *
 * <p>测试环境：本地 MySQL onepl_test_bootstrap（与 V1_3_0 / V1_2_5 IT 一致）。
 */
class V1_4_0FlywayIT extends PerformanceFlywayTestBase {

    @Autowired
    private ResourceLoader resourceLoader;

    /** 测试专用预置行的标识前缀（唯一避免与其它测试冲突）. */
    private static final String TEST_PLAN_ID = "V140_PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String TEST_PLAN_CODE = "V140_CODE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String TEST_KPI_SCHEME_ID = "V140_KS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String TEST_VALUE_ID = "V140_VAL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    /** 回填语义断言：预置 created_by='USER_BF_*' 行，期望 owner_emp_id 被回填等值. */
    private static final String BACKFILL_USER = "USER_BF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    /**
     * 每个用例后清理预置行（避免跨用例污染）.
     */
    @AfterEach
    void cleanupPreseed() {
        jdbc.update("DELETE FROM PERF_TARGET_PLAN WHERE id = ?", TEST_PLAN_ID);
        jdbc.update("DELETE FROM PERF_TARGET_VALUE WHERE id = ?", TEST_VALUE_ID);
    }

    /**
     * V1_4_0 脚本必须存在于 classpath:sql/ 目录下（物理文件守护）.
     *
     * <p>Red 阶段：文件不存在 → r.exists()=false → 断言失败.
     * <p>Green 阶段：文件创建后 → 断言通过.
     */
    @Test
    void v140ScriptFile_existsInClasspath() {
        Resource r = resourceLoader.getResource("classpath:sql/V1_4_0__perf_target_owner_cols.sql");
        assertThat(r.exists())
            .as("V1_4_0__perf_target_owner_cols.sql 必须存在于 classpath:sql/ 下")
            .isTrue();
    }

    /**
     * V1_4_0 必须在 flyway_schema_history 中登记成功.
     *
     * <p>Red 阶段：脚本不存在 → flyway 不会登记 1.4.0 → count=0 → 测试失败.
     * <p>Green 阶段：V1_4_0 创建后 → Spring 启动时自动 migrate → count=1.
     */
    @Test
    void v140Script_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.4.0' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * perf_target_plan / perf_target_value 均需有 owner_emp_id + owner_org_code 4 个列.
     *
     * <p>Red 阶段：字段不存在 → COUNT=0 → 断言失败.
     * <p>Green 阶段：ALTER TABLE 应用后 → COUNT=4.
     */
    @Test
    void v140_addsOwnerColumns_toTargetPlanAndValue() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA=DATABASE() " +
            "AND ((TABLE_NAME='PERF_TARGET_PLAN' AND COLUMN_NAME IN ('owner_emp_id','owner_org_code')) " +
            "  OR (TABLE_NAME='PERF_TARGET_VALUE' AND COLUMN_NAME IN ('owner_emp_id','owner_org_code')))",
            Integer.class);
        assertThat(count)
            .as("V1_4_0 后两表各自应有 owner_emp_id / owner_org_code 两列, 合计 4")
            .isEqualTo(4);
    }

    /**
     * perf_target_plan / perf_target_value 均需有 idx_owner_emp + idx_owner_org 4 个索引.
     *
     * <p>Green 阶段：ADD INDEX 应用后 → COUNT=4.
     */
    @Test
    void v140_addsOwnerIndexes_toTargetPlanAndValue() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, '.', INDEX_NAME)) " +
            "FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() " +
            "AND ((TABLE_NAME='PERF_TARGET_PLAN' AND INDEX_NAME IN ('idx_owner_emp','idx_owner_org')) " +
            "  OR (TABLE_NAME='PERF_TARGET_VALUE' AND INDEX_NAME IN ('idx_owner_emp','idx_owner_org')))",
            Integer.class);
        assertThat(count)
            .as("V1_4_0 后两表各自应有 idx_owner_emp / idx_owner_org 两索引, 合计 4")
            .isEqualTo(4);
    }

    /**
     * 回填语义：预置 created_by 非空、owner_emp_id NULL 行, 手动执行脚本等价的回填 UPDATE
     * 后 owner_emp_id 应等于 created_by. 验证 V1_4_0 脚本的 UPDATE 语义正确（历史数据补齐路径）.
     *
     * <p>与 V1_2_5FlywayIT.updateSemantics_fixesNullDeletedRow 同款模式：
     * <ul>
     *   <li>Spring 启动时 flyway migrate 已应用 V1_4_0，BeforeEach 预置的新 NULL 行不在其范围内</li>
     *   <li>故手工执行等价 UPDATE，验证脚本语义（"UPDATE ... SET owner_emp_id = created_by WHERE owner_emp_id IS NULL"）</li>
     * </ul>
     */
    @Test
    void v140_ownerEmpId_backfilled_fromCreatedBy() {
        // 预置：插一条 owner_emp_id IS NULL 的 target_value 行
        // 必填字段：id, plan_id, subject_type, subject_id, cycle_key, metric_code, target_value
        try {
            jdbc.update(
                "INSERT INTO PERF_TARGET_VALUE " +
                "(id, plan_id, subject_type, subject_id, cycle_key, metric_code, target_value, created_by) " +
                "VALUES (?, ?, 'EMP', 'V140_SBJ', '2026', 'V140_M', 100.0, ?)",
                TEST_VALUE_ID, TEST_PLAN_ID, BACKFILL_USER);
        } catch (DataAccessException e) {
            // 如果列还不存在，这条会挂 — 在 Green 后应能执行成功
            throw e;
        }

        // When：执行与 V1_4_0 等价的回填 UPDATE
        jdbc.update("UPDATE PERF_TARGET_VALUE SET owner_emp_id = created_by " +
                    "WHERE id = ? AND owner_emp_id IS NULL", TEST_VALUE_ID);

        // Then：owner_emp_id 应该被回填为 created_by 值
        String ownerEmpId = jdbc.queryForObject(
            "SELECT owner_emp_id FROM PERF_TARGET_VALUE WHERE id = ?",
            String.class, TEST_VALUE_ID);
        assertThat(ownerEmpId)
            .as("回填 UPDATE 应把 owner_emp_id 填为 created_by=" + BACKFILL_USER)
            .isEqualTo(BACKFILL_USER);
    }
}
