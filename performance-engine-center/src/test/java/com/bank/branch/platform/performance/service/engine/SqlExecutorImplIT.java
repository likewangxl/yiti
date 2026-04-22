package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SqlExecutorImpl 集成测试.
 *
 * <p>使用临时表 {@code t_perf_sql_exec_test}（@BeforeEach 创建，@AfterEach 删除）
 * 避免依赖任何业务表结构。
 */
class SqlExecutorImplIT extends PerformanceMapperTestBase {

    @Autowired
    private SqlExecutor sqlExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareTempTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_perf_sql_exec_test");
        jdbcTemplate.execute("CREATE TABLE t_perf_sql_exec_test ("
                + " k VARCHAR(32) NOT NULL,"
                + " v DECIMAL(20, 4) NOT NULL,"
                + " PRIMARY KEY (k))");
        jdbcTemplate.update("INSERT INTO t_perf_sql_exec_test(k, v) VALUES (?, ?)", "TEST_SQLE_A", new BigDecimal("100.00"));
        jdbcTemplate.update("INSERT INTO t_perf_sql_exec_test(k, v) VALUES (?, ?)", "TEST_SQLE_B", new BigDecimal("250.50"));
    }

    @AfterEach
    void dropTempTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_perf_sql_exec_test");
    }

    @Test
    @DisplayName("execute 返回 (base_key -> metric_value) 映射")
    void execute_returnsBaseKeyToMetricValueMap() {
        Map<String, BigDecimal> result = sqlExecutor.execute(
                "SELECT k AS base_key, v AS metric_value FROM t_perf_sql_exec_test WHERE k IN ('TEST_SQLE_A','TEST_SQLE_B')",
                new LinkedHashMap<>(),
                Duration.ofSeconds(30));

        assertThat(result).hasSize(2);
        assertThat(result.get("TEST_SQLE_A")).isEqualByComparingTo("100.00");
        assertThat(result.get("TEST_SQLE_B")).isEqualByComparingTo("250.50");
    }

    @Test
    @DisplayName("execute 支持命名参数（:name 风格）")
    void execute_supportsNamedParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", "TEST_SQLE_A");
        Map<String, BigDecimal> result = sqlExecutor.execute(
                "SELECT k AS base_key, v AS metric_value FROM t_perf_sql_exec_test WHERE k = :key",
                params,
                Duration.ofSeconds(30));

        assertThat(result).hasSize(1);
        assertThat(result.get("TEST_SQLE_A")).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("execute 结果列名不符合约定时抛 METRIC_CALC_LOGIC_INVALID")
    void execute_whenColumnNamesInvalid_throwsCalcLogicInvalid() {
        assertThatThrownBy(() -> sqlExecutor.execute(
                "SELECT k AS wrong_key, v AS wrong_value FROM t_perf_sql_exec_test",
                new LinkedHashMap<>(),
                Duration.ofSeconds(30)))
                .isInstanceOf(PerfException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.throwable(PerfException.class))
                .extracting(PerfException::getErrorCode)
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("execute SQL 执行异常时抛 METRIC_CALC_LOGIC_INVALID")
    void execute_whenSqlFails_throwsCalcLogicInvalid() {
        assertThatThrownBy(() -> sqlExecutor.execute(
                "SELECT base_key, metric_value FROM t_nonexistent_table_42",
                new LinkedHashMap<>(),
                Duration.ofSeconds(30)))
                .isInstanceOf(PerfException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.throwable(PerfException.class))
                .extracting(PerfException::getErrorCode)
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }
}
