package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SqlValidator 单元测试（黑名单拦截 + SELECT/CTE 白名单）.
 */
class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @ParameterizedTest(name = "拦截 DML/DDL: {0}")
    @ValueSource(strings = {
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET a=1 WHERE b=2",
            "DELETE FROM t WHERE id=1",
            "DROP TABLE t",
            "TRUNCATE TABLE t",
            "ALTER TABLE t ADD COLUMN x INT",
            "CREATE TABLE x(a INT)",
            "RENAME TABLE t TO t2",
            "GRANT SELECT ON t TO u",
            "REVOKE SELECT ON t FROM u",
            "CALL my_proc()",
            // 混合大小写 + 前导空白也必须拦截
            "   delete FROM t",
            "/* comment */ INSERT INTO t VALUES(1)"
    })
    @DisplayName("validate 拦截 DML/DDL/存储过程调用")
    void validate_rejectsDmlDdl(String sql) {
        assertThatThrownBy(() -> validator.validate(sql))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @ParameterizedTest(name = "放行 SELECT/CTE: {0}")
    @ValueSource(strings = {
            "SELECT emp_id AS base_key, 1 AS metric_value FROM t_emp",
            "  select * FROM dual",
            "WITH cte AS (SELECT 1 AS base_key, 2 AS metric_value) SELECT * FROM cte",
            "/* metric_v1 */ SELECT base_key, metric_value FROM t WHERE x > 0"
    })
    @DisplayName("validate 放行 SELECT / CTE")
    void validate_allowsSelectAndCte(String sql) {
        assertThatCode(() -> validator.validate(sql)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate 空 SQL 抛 METRIC_CALC_LOGIC_INVALID")
    void validate_rejectsBlankSql() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("validate 拦截 SELECT 之后再 ; DELETE 的多语句攻击")
    void validate_rejectsMultiStatementInjection() {
        assertThatThrownBy(() -> validator.validate(
                "SELECT 1 AS base_key, 2 AS metric_value; DELETE FROM t"))
                .isInstanceOf(PerfException.class);
    }
}
