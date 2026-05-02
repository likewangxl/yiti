package com.bank.branch.platform.report.support;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.exception.RptException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SqlSafeValidator 边界用例守护测试（Task M4.1.1，Red）.
 *
 * <p>覆盖 plan L2204-L2310 「≥15 case」要求，分组：
 * <ol>
 *   <li>通过用例（3）：simple SELECT、Whitelist JOIN、Subquery depth ≤ 3</li>
 *   <li>白名单拒绝（1）：未登记表 → RPT-42002</li>
 *   <li>禁用关键字（5）：DROP / DELETE / UPDATE / INSERT / 大小写不敏感 → RPT-42003</li>
 *   <li>仅 SELECT（1）：SHOW TABLES → RPT-42007</li>
 *   <li>UNION / EXCEPT / INTERSECT 拒绝（1）→ RPT-42001</li>
 *   <li>子查询深度（1）：4 层 → RPT-42001</li>
 *   <li>LIMIT 标准化（3）：无 LIMIT 自动 1000 / 超 1000 截断 / &lt;1000 保留</li>
 *   <li>SQL 长度上限（1）：&gt;5000 → RPT-42008</li>
 *   <li>解析失败（1）：非法语法 → RPT-42001</li>
 * </ol>
 *
 * <p>共 16 个 case，全部走 {@link SqlSafeValidator#validateAndNormalize(String)} 单一入口。
 */
class SqlSafeValidatorTest {

    /** 测试专用校验器：3 张白名单表 + 4 个禁用关键字 + maxRows=1000 + maxSqlLength=5000 + depth=3. */
    private final SqlSafeValidator v = new SqlSafeValidator(
            List.of("cust_master", "kpi_result", "metric_def"),
            List.of("DROP", "DELETE", "UPDATE", "INSERT"),
            1000, 5000, 3);

    // =====================================================================
    // 1) 通过用例
    // =====================================================================

    @Test
    void validate_simpleSelect_passes() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM CUST_MASTER WHERE id=1");
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getNormalizedSql()).contains("LIMIT 1000");
    }

    @Test
    void validate_selectWithWhitelistJoin_passes() {
        SqlSafeResult r = v.validateAndNormalize(
                "SELECT k.score FROM KPI_RESULT k JOIN CUST_MASTER c ON k.emp_id = c.id LIMIT 100");
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getReferencedTables()).contains("KPI_RESULT", "CUST_MASTER");
    }

    @Test
    void validate_selectWithSubquery_passesIfDepthLE3() {
        SqlSafeResult r = v.validateAndNormalize(
                "SELECT * FROM CUST_MASTER WHERE id IN (SELECT emp_id FROM KPI_RESULT) LIMIT 100");
        assertThat(r.isAllowed()).isTrue();
    }

    // =====================================================================
    // 2) 拒绝用例（白名单）
    // =====================================================================

    @Test
    void validate_tableNotInWhitelist_rejects42002() {
        assertThatThrownBy(() -> v.validateAndNormalize("SELECT * FROM secret_table"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42002");
    }

    // =====================================================================
    // 3) 拒绝用例（禁用关键字）
    // =====================================================================

    @Test
    void validate_dropKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("DROP TABLE CUST_MASTER"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42003");
    }

    @Test
    void validate_deleteKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("DELETE FROM CUST_MASTER"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42003");
    }

    @Test
    void validate_updateKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("UPDATE CUST_MASTER SET name='x'"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42003");
    }

    @Test
    void validate_insertKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("INSERT INTO CUST_MASTER VALUES (1)"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42003");
    }

    @Test
    void validate_lowercaseKeyword_rejects42003() {
        assertThatThrownBy(() -> v.validateAndNormalize("drop table CUST_MASTER"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42003");
    }

    // =====================================================================
    // 4) 仅 SELECT
    // =====================================================================

    @Test
    void validate_nonSelectStatement_rejects42007() {
        // SHOW TABLES 不命中 4 个禁用关键字，但属于非 Select 语句（JSqlParser 解析为 Statement
        // 但不是 Select 子类型），应该被 SQL_ONLY_SELECT_ALLOWED 拒绝
        assertThatThrownBy(() -> v.validateAndNormalize("SHOW TABLES"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42007");
    }

    // =====================================================================
    // 5) UNION / EXCEPT / INTERSECT 拒绝
    // =====================================================================

    @Test
    void validate_unionAll_rejects42001() {
        assertThatThrownBy(() -> v.validateAndNormalize(
                "SELECT * FROM CUST_MASTER UNION ALL SELECT * FROM KPI_RESULT"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42001");
    }

    // =====================================================================
    // 6) 子查询深度（含 V1.0 M5 顺手补的"3 通过 / 4 拒绝"精确边界）
    // =====================================================================

    @Test
    void validate_subqueryDepth4_rejects42001() {
        // 5 层 SELECT 嵌套 = 子查询深度 5 > 3，应当拒绝
        String sql = "SELECT * FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER))))";
        assertThatThrownBy(() -> v.validateAndNormalize(sql))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42001");
    }

    /** M4 reviewer 观察项 #1：精确深度 3 应通过（边界等于上限）. */
    @Test
    void validate_subqueryDepthExactly3_passes() {
        // computeDepth 起始 depth=1，3 层 SELECT 嵌套（主 + 2 子查询）= depth 3 = 上限，应通过
        String sql = "SELECT * FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER))";
        SqlSafeResult r = v.validateAndNormalize(sql);
        assertThat(r.isAllowed()).isTrue();
    }

    /** M4 reviewer 观察项 #1：精确深度 4 应拒绝（恰好越过上限）. */
    @Test
    void validate_subqueryDepthExactly4_rejects42001() {
        // 4 层 SELECT 嵌套（主 + 3 子查询）= depth 4 > 3，恰好越过上限
        String sql = "SELECT * FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER WHERE id IN "
                + "(SELECT id FROM CUST_MASTER)))";
        assertThatThrownBy(() -> v.validateAndNormalize(sql))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42001");
    }

    // =====================================================================
    // 7) LIMIT 自动追加 / clamp / 保留
    // =====================================================================

    @Test
    void validate_noLimit_autoAppends1000() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM CUST_MASTER");
        assertThat(r.getNormalizedSql()).endsWith("LIMIT 1000");
    }

    @Test
    void validate_limitOver1000_clampsTo1000() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM CUST_MASTER LIMIT 5000");
        assertThat(r.getNormalizedSql()).endsWith("LIMIT 1000");
    }

    @Test
    void validate_limitUnder1000_keeps() {
        SqlSafeResult r = v.validateAndNormalize("SELECT * FROM CUST_MASTER LIMIT 50");
        assertThat(r.getNormalizedSql()).endsWith("LIMIT 50");
    }

    // =====================================================================
    // 8) SQL 长度上限
    // =====================================================================

    @Test
    void validate_sqlLengthOver5000_rejects42008() {
        // 5100 个 '1' 字符，远超 5000 上限
        String longSql = "SELECT * FROM CUST_MASTER WHERE id=" + "1".repeat(5100);
        assertThatThrownBy(() -> v.validateAndNormalize(longSql))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42008");
    }

    // =====================================================================
    // 9) 解析失败
    // =====================================================================

    @Test
    void validate_invalidSyntax_rejects42001() {
        assertThatThrownBy(() -> v.validateAndNormalize("SELECT FROM ;;"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-42001");
    }
}
