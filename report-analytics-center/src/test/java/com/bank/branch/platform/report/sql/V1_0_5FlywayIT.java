package com.bank.branch.platform.report.sql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1_0_5 Flyway 守护测试（Task M5.1.1）.
 *
 * <p>守护 rpt_export_task 12 个关键列存在 + V1_0_5 脚本登记到独立的 flyway_schema_history_rpt：
 * <ul>
 *   <li>id / export_type / params_json / status / file_key / file_size / row_count
 *       / expire_at / operator_id / error_msg / created_time / updated_time</li>
 * </ul>
 *
 * <p><b>脚本职责说明</b>：rpt_export_task 表已在 V1_0_0__rpt_init.sql §4 创建，
 * V1_0_5 仅作为 idempotent 兜底（CREATE TABLE IF NOT EXISTS 不会重复创建），
 * 同时让 M5 阶段在 flyway_schema_history_rpt 留一行 1.0.5 success=1 记录，
 * 与 plan §M5.1 保持脚本节奏一致。
 *
 * <p>Red：脚本不存在 → information_schema 查询返回 12（表已在 V1_0_0 建好），
 * 但 flyway_schema_history_rpt 没有 1.0.5 记录 → 断言失败.
 * <p>Green：V1_0_5__rpt_export_task.sql 落地后 → Spring 启动自动 migrate → 断言通过.
 */
class V1_0_5FlywayIT extends ReportFlywayTestBase {

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void v105ScriptFile_existsInClasspath() {
        Resource r = resourceLoader.getResource("classpath:sql/report/V1_0_5__rpt_export_task.sql");
        assertThat(r.exists())
            .as("V1_0_5__rpt_export_task.sql 必须存在于 classpath:sql/report/ 下")
            .isTrue();
    }

    @Test
    void v105Script_registered_inFlywayHistory() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history_rpt WHERE version='1.0.5' AND success=1",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rptExportTaskShouldHave12RequiredColumns() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema=DATABASE() " +
                "AND table_name='rpt_export_task' " +
                "AND column_name IN ('id','export_type','params_json','status'," +
                "'file_key','file_size','row_count','expire_at','operator_id'," +
                "'error_msg','created_time','updated_time')",
            Integer.class);
        assertThat(cnt).isEqualTo(12);
    }
}
