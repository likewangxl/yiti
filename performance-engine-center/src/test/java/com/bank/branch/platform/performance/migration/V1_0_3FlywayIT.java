package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
