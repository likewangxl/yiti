package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScreenQueryEngine 真库执行 IT（onepl_test_bootstrap；只读源与主源同库不同连接，
 * 故不用 @Transactional 回滚，改为 @AfterEach 按 TEST_SCR_ 前缀清理）.
 */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
class ScreenQueryEngineIT {

    @Autowired
    private ScreenQueryEngine engine;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String version;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanup();
        // 与引擎同口径取当前版本（无 SYS_CONTROL 行时回退 V1），保证插入行可被查中
        version = jdbc.queryForObject(
                "SELECT COALESCE((SELECT current_version FROM SYS_CONTROL "
                        + "WHERE scope_dim = 'EMP' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')",
                String.class);
        jdbc.update("INSERT INTO EMP_INDEX_RESULT (data_date, version, emp_id, val_1) VALUES (?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-01"), version, "TEST_SCR_E1", new BigDecimal("111.5"));
        jdbc.update("INSERT INTO EMP_INDEX_RESULT (data_date, version, emp_id, val_1) VALUES (?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-02"), version, "TEST_SCR_E1", new BigDecimal("222.5"));

        // KPI_DETAIL 种子：指标定义 1 条（M2 故意无定义验证名称回落）+ 快照两日得分
        jdbc.update("INSERT INTO PERF_METRIC_DEF (id, metric_code, metric_name, base_dim, metric_level,"
                        + " calc_freq, calc_mode, status, deleted) VALUES (?, ?, ?, 'EMP', 1, 'DAY', 'AUTO', 'ACTIVE', 0)",
                "TEST_RPT_KD_MD1", "TEST_RPT_KD_M1", "TEST_RPT_KD细项一");
        // 2026-07-01：仅 M1（旧快照日，SNAPSHOT 不应取到）
        jdbc.update("INSERT INTO PERF_KPI_SCORE (data_date, scheme_code, metric_code, subject_type, subject_id,"
                        + " actual_value, target_value, weight, score) VALUES (?, ?, ?, 'EMP', ?, ?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-01"), "TEST_RPT_KD_SCH", "TEST_RPT_KD_M1", "TEST_RPT_KD_E1",
                new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("0.6"), new BigDecimal("30"));
        // 2026-07-02：M1 正常 + M2 目标值为 0（完成率应为 NULL）且实际超额（缺口为负）
        jdbc.update("INSERT INTO PERF_KPI_SCORE (data_date, scheme_code, metric_code, subject_type, subject_id,"
                        + " actual_value, target_value, weight, score) VALUES (?, ?, ?, 'EMP', ?, ?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-02"), "TEST_RPT_KD_SCH", "TEST_RPT_KD_M1", "TEST_RPT_KD_E1",
                new BigDecimal("80"), new BigDecimal("100"), new BigDecimal("0.6"), new BigDecimal("48"));
        jdbc.update("INSERT INTO PERF_KPI_SCORE (data_date, scheme_code, metric_code, subject_type, subject_id,"
                        + " actual_value, target_value, weight, score) VALUES (?, ?, ?, 'EMP', ?, ?, ?, ?, ?)",
                java.sql.Date.valueOf("2026-07-02"), "TEST_RPT_KD_SCH", "TEST_RPT_KD_M2", "TEST_RPT_KD_E1",
                new BigDecimal("5"), new BigDecimal("0"), new BigDecimal("0.4"), new BigDecimal("20"));
    }

    @AfterEach
    void cleanup() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        jdbc.update("DELETE FROM EMP_INDEX_RESULT WHERE emp_id LIKE 'TEST_SCR_%'");
        jdbc.update("DELETE FROM PERF_KPI_SCORE WHERE scheme_code LIKE 'TEST_RPT_KD_%'");
        jdbc.update("DELETE FROM PERF_METRIC_DEF WHERE metric_code LIKE 'TEST_RPT_KD_%'");
    }

    private RptScreenDatasource wideDs() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("WIDE_TABLE");
        ds.setDsType("TIMESERIES");
        ds.setConfigJson("{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_TEST\",\"metricName\":\"存款余额\",\"slot\":1}]}");
        return ds;
    }

    private ScreenDataReqDTO req(String period, String dateFrom, String dateTo) {
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod(period);
        r.setDateFrom(dateFrom);
        r.setDateTo(dateTo);
        r.setContextParams(Map.of("empId", "TEST_SCR_E1"));
        return r;
    }

    @Test
    void wideLatest_returnsNewestSingleRow() {
        ScreenDataRespDTO resp = engine.query(wideDs(), req("LATEST", null, null));
        assertThat(resp.getColumns()).containsExactly("data_date", "存款余额");
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0).get(0)).isEqualTo("2026-07-02");
        assertThat(new BigDecimal(resp.getRows().get(0).get(1).toString())).isEqualByComparingTo("222.5");
    }

    @Test
    void wideRange_returnsRowsAscending() {
        ScreenDataRespDTO resp = engine.query(wideDs(), req("RANGE", "2026-07-01", "2026-07-02"));
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0).get(0)).isEqualTo("2026-07-01");
        assertThat(resp.getRows().get(1).get(0)).isEqualTo("2026-07-02");
    }

    @Test
    void kpiDetailSnapshot_latestDateAllItems_nameFallbackAndComputedCols() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("KPI_DETAIL");
        ds.setDsType("SINGLE");
        ds.setConfigJson("{\"schemaVersion\":2,\"schemeCode\":\"TEST_RPT_KD_SCH\","
                + "\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}");
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod("LATEST");
        r.setContextParams(Map.of("empId", "TEST_RPT_KD_E1"));

        ScreenDataRespDTO resp = engine.query(ds, r);

        assertThat(resp.getColumns()).containsExactly(
                "metric_code", "细项名称", "目标值", "实际值", "权重", "得分", "完成率", "缺口");
        // 仅最新快照日 2026-07-02 的 2 个细项（ORDER BY metric_code：M1 在前）
        assertThat(resp.getRows()).hasSize(2);
        var m1 = resp.getRows().get(0);
        assertThat(m1.get(0)).isEqualTo("TEST_RPT_KD_M1");
        assertThat(m1.get(1)).isEqualTo("TEST_RPT_KD细项一");
        assertThat(new BigDecimal(m1.get(6).toString())).isEqualByComparingTo("80.00"); // 完成率=80/100*100
        assertThat(new BigDecimal(m1.get(7).toString())).isEqualByComparingTo("20");    // 缺口=100-80
        var m2 = resp.getRows().get(1);
        assertThat(m2.get(0)).isEqualTo("TEST_RPT_KD_M2");
        assertThat(m2.get(1)).isEqualTo("TEST_RPT_KD_M2"); // 无指标定义 → 名称回落 metric_code
        assertThat(m2.get(6)).isNull();                    // target=0 → 完成率 NULL
        assertThat(new BigDecimal(m2.get(7).toString())).isEqualByComparingTo("-5"); // 超额缺口为负
    }

    @Test
    void kpiDetailTrend_range_pivotPerMetricColumn() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("KPI_DETAIL");
        ds.setDsType("TIMESERIES");
        ds.setConfigJson("{\"schemaVersion\":2,\"schemeCode\":\"TEST_RPT_KD_SCH\","
                + "\"subjectType\":\"EMP\",\"mode\":\"TREND\",\"metrics\":["
                + "{\"metricCode\":\"TEST_RPT_KD_M1\",\"metricName\":\"细项一\"},"
                + "{\"metricCode\":\"TEST_RPT_KD_M2\",\"metricName\":\"细项二\"}]}");
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod("RANGE");
        r.setDateFrom("2026-07-01");
        r.setDateTo("2026-07-02");
        r.setContextParams(Map.of("empId", "TEST_RPT_KD_E1"));

        ScreenDataRespDTO resp = engine.query(ds, r);

        assertThat(resp.getColumns()).containsExactly("data_date", "细项一", "细项二");
        assertThat(resp.getRows()).hasSize(2);
        // 07-01：仅 M1 有得分 30，M2 无行 → NULL
        assertThat(resp.getRows().get(0).get(0)).isEqualTo("2026-07-01");
        assertThat(new BigDecimal(resp.getRows().get(0).get(1).toString())).isEqualByComparingTo("30");
        assertThat(resp.getRows().get(0).get(2)).isNull();
        // 07-02：M1=48、M2=20
        assertThat(resp.getRows().get(1).get(0)).isEqualTo("2026-07-02");
        assertThat(new BigDecimal(resp.getRows().get(1).get(1).toString())).isEqualByComparingTo("48");
        assertThat(new BigDecimal(resp.getRows().get(1).get(2).toString())).isEqualByComparingTo("20");
    }

    @Test
    void customSql_countByEmpPlaceholder_returnsSingleValue() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("CUSTOM_SQL");
        ds.setDsType("SINGLE");
        ds.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM EMP_INDEX_RESULT WHERE emp_id = #{empId}\","
                + "\"dateCol\":null}");
        ScreenDataRespDTO resp = engine.query(ds, req("LATEST", null, null));
        assertThat(resp.getColumns()).containsExactly("cnt");
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0).get(0).toString()).isEqualTo("2");
    }
}
