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

    @Test
    void toValidatable_illegalParamName_throws43002() {
        // 补充覆盖 toValidatable 的非法参数名分支（原实现已有该分支，但缺测试用例）
        assertThatThrownBy(() -> ScreenSqlTemplate.toValidatable("SELECT 1 FROM t WHERE a = #{evil}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void parse_emptyOrDanglingHash_throws43002() {
        // # 是 MySQL 单行注释符：空占位 #{} 与裸 # 都不匹配占位正则，若放任透传会把行尾 SQL 静默注释掉，
        // 必须在替换完成后对残留 # 做兜底校验并拒绝，而不是任其改变 SQL 语义。
        assertThatThrownBy(() -> ScreenSqlTemplate.parse("SELECT * FROM t WHERE a = #{}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
        assertThatThrownBy(() -> ScreenSqlTemplate.parse("SELECT * FROM t WHERE a = 1 # dangling comment"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void toValidatable_residualHash_throws43002() {
        // 同 parse_emptyOrDanglingHash_throws43002，覆盖 toValidatable 一侧的残留 # 兜底校验
        assertThatThrownBy(() -> ScreenSqlTemplate.toValidatable("SELECT * FROM t WHERE a = #{}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
        assertThatThrownBy(() -> ScreenSqlTemplate.toValidatable("SELECT * FROM t WHERE a = 1 # dangling comment"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }
}
