package com.bank.branch.platform.report.sql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_0_0 Flyway 基线脚本集成测试（Task M0.2.1）.
 *
 * <p>守护 3 张自有表 + 关键字段 + 1 条 flyway_schema_history 登记：
 * <ul>
 *   <li>rpt_saved_query: 9 关键列 (id/emp_id/name/dim/subject_ids/metric_codes/version/created_time/updated_time)</li>
 *   <li>sql_probe_history: 9 关键列 (id/emp_id/sql_text/remark/row_count/execution_time_ms/status/error_msg/created_time)</li>
 *   <li>rpt_snapshot_task: 表存在</li>
 *   <li>flyway_schema_history: version=1.0.0 success=1</li>
 * </ul>
 *
 * <p>Red：脚本不存在 → information_schema 查询返回 0 → 断言失败.
 * <p>Green：V1_0_0__rpt_init.sql 创建后 → Spring 启动自动 migrate → 断言通过.
 */
class V1_0_0FlywayIT extends ReportFlywayTestBase {

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void v100ScriptFile_existsInClasspath() {
        Resource r = resourceLoader.getResource("classpath:sql/V1_0_0__rpt_init.sql");
        assertThat(r.exists())
            .as("V1_0_0__rpt_init.sql 必须存在于 classpath:sql/ 下")
            .isTrue();
    }

    @Test
    void v100Script_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.0.0' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rptSavedQueryShouldExistWithRequiredColumns() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema=DATABASE() " +
            "AND table_name='rpt_saved_query' " +
            "AND column_name IN ('id','emp_id','name','dim','subject_ids','metric_codes','version','created_time','updated_time')",
            Integer.class);
        assertThat(count).isEqualTo(9);
    }

    @Test
    void sqlProbeHistoryShouldExistWithRequiredColumns() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema=DATABASE() " +
            "AND table_name='sql_probe_history' " +
            "AND column_name IN ('id','emp_id','sql_text','remark','row_count','execution_time_ms','status','error_msg','created_time')",
            Integer.class);
        assertThat(count).isEqualTo(9);
    }

    @Test
    void rptSnapshotTaskShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema=DATABASE() AND table_name='rpt_snapshot_task'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
