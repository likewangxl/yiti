package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.dto.resp.ScreenKpiSchemeRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KPI 方案下拉真库 IT（onepl_test_bootstrap，TEST_RPT_ 前缀种子自建自清）：
 * 仅返回 ACTIVE 方案，DISABLED 不出现在下拉中.
 */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
class ScreenKpiSchemeListIT {

    @Autowired
    private ScreenDatasourceService service;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanup();
        jdbc.update("INSERT INTO PERF_KPI_SCHEME (id, scheme_code, scheme_name, cycle_type, status)"
                + " VALUES ('TEST_RPT_KD_S1', 'TEST_RPT_KD_SC1', 'TEST_RPT测试方案甲', 'MONTHLY', 'ACTIVE')");
        jdbc.update("INSERT INTO PERF_KPI_SCHEME (id, scheme_code, scheme_name, cycle_type, status)"
                + " VALUES ('TEST_RPT_KD_S2', 'TEST_RPT_KD_SC2', 'TEST_RPT测试方案乙', 'MONTHLY', 'DISABLED')");
    }

    @AfterEach
    void cleanup() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        jdbc.update("DELETE FROM PERF_KPI_SCHEME WHERE scheme_code LIKE 'TEST_RPT_KD_%'");
    }

    @Test
    void listKpiSchemes_returnsOnlyActiveWithCodeAndName() {
        List<ScreenKpiSchemeRespDTO> result = service.listKpiSchemes();

        // ACTIVE 方案必须在下拉中且带名称
        assertThat(result)
                .filteredOn(s -> "TEST_RPT_KD_SC1".equals(s.getSchemeCode()))
                .hasSize(1)
                .first()
                .extracting(ScreenKpiSchemeRespDTO::getSchemeName)
                .isEqualTo("TEST_RPT测试方案甲");
        // DISABLED 方案绝不出现
        assertThat(result).noneMatch(s -> "TEST_RPT_KD_SC2".equals(s.getSchemeCode()));
    }
}
