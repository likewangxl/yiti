package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1_0_3 Flyway 迁移集成测试.
 *
 * <p>测试环境：本地 MySQL onepl_test_v103（Docker 不可用，降级）。
 * <p>验证 V1_0_3 脚本在全量迁移后正确建立索引和新增字段。
 */
class V1_0_3FlywayIT extends PerformanceFlywayTestBase {

    @Test
    void sysControl_uniqueKey_includesCurrentVersion() {
        List<String> cols = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_control' " +
            "AND INDEX_NAME='uk_scope_dim_date_version' ORDER BY SEQ_IN_INDEX",
            String.class);
        assertThat(cols).containsExactly("scope_dim", "latest_data_date", "current_version");
    }

    @Test
    void perfMetricDef_slotConflict_rejectedByDatabase() {
        // 插入第一条（未删除，slot=1）
        jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, deleted, metric_level, calc_freq, calc_mode, status) " +
                    "VALUES ('FWIT_T1','FWIT_SLOT_1','T1','EMP',1,0,1,'DAY','AUTO','ACTIVE')");

        // 同维度同 slot 的第二条（deleted=0）应因 UK 冲突被拒绝
        assertThatThrownBy(() ->
            jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, deleted, metric_level, calc_freq, calc_mode, status) " +
                        "VALUES ('FWIT_T2','FWIT_SLOT_2','T2','EMP',1,0,1,'DAY','AUTO','ACTIVE')")
        ).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);

        // 软删 T1 后，再插入同 slot 的 T3（deleted=0）应被允许
        jdbc.update("UPDATE perf_metric_def SET deleted=1 WHERE id='FWIT_T1'");
        jdbc.update("INSERT INTO perf_metric_def (id, metric_code, metric_name, base_dim, val_slot, deleted, metric_level, calc_freq, calc_mode, status) " +
                    "VALUES ('FWIT_T3','FWIT_SLOT_3','T3','EMP',1,0,1,'DAY','AUTO','ACTIVE')");

        // 清理测试数据
        jdbc.update("DELETE FROM perf_metric_def WHERE id IN ('FWIT_T1','FWIT_T2','FWIT_T3')");
    }
}
