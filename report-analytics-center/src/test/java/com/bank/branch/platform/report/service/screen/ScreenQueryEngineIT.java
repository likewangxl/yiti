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
    }

    @AfterEach
    void cleanup() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        jdbc.update("DELETE FROM EMP_INDEX_RESULT WHERE emp_id LIKE 'TEST_SCR_%'");
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
