package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScreenQueryEngine SQL 构造纯逻辑单测（不触库，DataSource 传 null）.
 */
class ScreenQueryEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    private final ScreenQueryEngine engine = new ScreenQueryEngine(
            null,
            List.of("EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT", "KPI_RESULT",
                    "SYS_CONTROL", "EXT_ORG_INFO", "ACT_RU_TASK"),
            List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT"),
            1000);

    private ScreenDataReqDTO req(String period, Map<String, String> ctx) {
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod(period);
        r.setContextParams(ctx);
        return r;
    }

    // ===== WIDE_TABLE =====

    @Test
    void buildWide_latest_ordersByDateDescLimit1_withAliasAndVersion() {
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("FROM EMP_INDEX_RESULT");
        assertThat(q.sql()).contains("val_3 AS `存款余额`");
        assertThat(q.sql()).contains("emp_id = ?");
        assertThat(q.sql()).contains("COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = 'EMP'");
        assertThat(q.sql()).endsWith("ORDER BY data_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001");
    }

    @Test
    void buildWide_last6mEom_addsEomFilterAndRange() {
        String cfg = "{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\",\"subjectParam\":\"orgCode\","
                + "\"metrics\":[{\"metricCode\":\"M_0002\",\"metricName\":\"贷款余额\",\"slot\":7}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LAST_6M_EOM", Map.of("orgCode", "610100")), 1000, TODAY);
        assertThat(q.sql()).contains("data_date BETWEEN ? AND ?");
        assertThat(q.sql()).contains("data_date = LAST_DAY(data_date)");
        assertThat(q.sql()).endsWith("ORDER BY data_date LIMIT 1000");
        assertThat(q.params()).containsExactly("610100", LocalDate.of(2026, 1, 1), TODAY);
    }

    @Test
    void buildWide_tableNotAllowed_throws43009() {
        String cfg = "{\"table\":\"SECRET_T\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"x\",\"slot\":1}]}";
        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWide_missingContextParam_throws43010() {
        // 上下文参数缺失属"入参校验失败"，应返回 43010（缺少必填上下文参数），与真实执行失败 43008 语义分离
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"x\",\"slot\":1}]}";
        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43010");
    }

    // ===== KPI_RESULT =====

    @Test
    void buildKpi_latest_rowNumberDedupByAsOfDate() {
        var q = engine.build("KPI_RESULT", "{\"cycleType\":\"MONTHLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC)");
        assertThat(q.sql()).contains("FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?");
        assertThat(q.sql()).endsWith("ORDER BY cycle_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001", "MONTHLY");
    }

    @Test
    void buildKpi_illegalCycleType_throws43009() {
        assertThatThrownBy(() -> engine.build("KPI_RESULT", "{\"cycleType\":\"HOURLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    // ===== CUSTOM_SQL =====

    @Test
    void buildCustom_placeholdersBoundInOrder_andWrappedWithLimit() {
        String cfg = "{\"sql\":\"SELECT org_code, COUNT(*) AS cnt FROM ACT_RU_TASK"
                + " WHERE tenant_id_ = #{orgCode} AND create_time_ BETWEEN #{dateFrom} AND #{dateTo}"
                + " GROUP BY org_code\",\"dateCol\":null}";
        var q = engine.build("CUSTOM_SQL", cfg,
                req("LAST_10D", Map.of("orgCode", "610100")), 10, TODAY);
        assertThat(q.sql()).startsWith("SELECT * FROM (");
        assertThat(q.sql()).endsWith(") rpt_scr_q LIMIT 10");
        assertThat(q.sql()).doesNotContain("#{");
        assertThat(q.params()).containsExactly("610100", LocalDate.of(2026, 7, 3), TODAY);
    }

    @Test
    void buildCustom_tableNotWhitelisted_throws43002() {
        String cfg = "{\"sql\":\"SELECT * FROM PT_USER\",\"dateCol\":null}";
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void buildCustom_missingContextParam_throws43010() {
        // 自定义 SQL 用了 #{empId}/#{orgCode} 占位但上下文未传，同属入参校验失败 → 43010
        String cfg = "{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK WHERE assignee_ = #{empId}\",\"dateCol\":null}";
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43010");
    }

    @Test
    void build_unknownSourceKind_throws43009() {
        assertThatThrownBy(() -> engine.build("MAGIC", "{}", req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void validateCustomSql_validatorError_convergesTo43002() {
        // 语法非法 SQL：SqlSafeValidator 会抛 RPT-42001，引擎必须收敛为 43002
        assertThatThrownBy(() -> engine.validateCustomSql("SELECT FROM WHERE"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }
}
