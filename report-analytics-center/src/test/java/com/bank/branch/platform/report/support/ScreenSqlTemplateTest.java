package com.bank.branch.platform.report.support;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScreenSqlTemplate 占位参数工具单测.
 */
class ScreenSqlTemplateTest {

    @Test
    void parse_replacesPlaceholdersInOrder() {
        ScreenSqlTemplate.Parsed p = ScreenSqlTemplate.parse(
                "SELECT a FROM t WHERE org = #{orgCode} AND d BETWEEN #{dateFrom} AND #{dateTo}");
        assertThat(p.jdbcSql()).isEqualTo("SELECT a FROM t WHERE org = ? AND d BETWEEN ? AND ?");
        assertThat(p.paramNames()).containsExactly("orgCode", "dateFrom", "dateTo");
    }

    @Test
    void parse_repeatedPlaceholder_collectedTwice() {
        ScreenSqlTemplate.Parsed p = ScreenSqlTemplate.parse(
                "SELECT * FROM t WHERE a = #{empId} OR b = #{empId}");
        assertThat(p.jdbcSql()).isEqualTo("SELECT * FROM t WHERE a = ? OR b = ?");
        assertThat(p.paramNames()).containsExactly("empId", "empId");
    }

    @Test
    void parse_illegalParamName_throws43002() {
        assertThatThrownBy(() -> ScreenSqlTemplate.parse("SELECT * FROM t WHERE a = #{evil}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void parse_noPlaceholder_passthrough() {
        ScreenSqlTemplate.Parsed p = ScreenSqlTemplate.parse("SELECT 1 FROM DUAL");
        assertThat(p.jdbcSql()).isEqualTo("SELECT 1 FROM DUAL");
        assertThat(p.paramNames()).isEmpty();
    }

    @Test
    void toValidatable_substitutesLiteral() {
        String v = ScreenSqlTemplate.toValidatable("SELECT a FROM t WHERE org = #{orgCode}");
        assertThat(v).isEqualTo("SELECT a FROM t WHERE org = '1'");
    }
}
