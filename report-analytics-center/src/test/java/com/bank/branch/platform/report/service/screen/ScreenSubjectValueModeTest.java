package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.performance.api.BranchDashboardBatchQueryApi;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardMetricContractDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardQualityDTO;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenSubjectValueModeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    @Test
    void immutableQualityExposesExclusiveSubjectValueModeOnlyForTestSnapshot() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        BranchDashboardBatchDTO testSnapshot = snapshot("TEST");
        when(batchApi.latest("GROUP_TEST", List.of("001", "002")))
                .thenReturn(Optional.of(testSnapshot));

        ScreenQueryEngine engine = engine(batchApi);
        ScreenDataRespDTO testResponse = engine.queryAt(
                datasource(config()), request(), TODAY, 10);

        assertThat(testResponse.getQuality().getSubjectValueMode()).isEqualTo("EXCLUSIVE");

        BranchDashboardBatchDTO prodSnapshot = snapshot("PROD");
        when(batchApi.latest("GROUP_TEST", List.of("001", "002")))
                .thenReturn(Optional.of(prodSnapshot));

        ScreenDataRespDTO prodResponse = engine.queryAt(
                datasource(config()), request(), TODAY, 10);

        assertThat(prodResponse.getQuality().getSubjectValueMode()).isNull();
    }

    private static BranchDashboardBatchDTO snapshot(String classification) {
        return BranchDashboardBatchDTO.builder()
                .batchId("TEST-SUBJECT-MODE")
                .groupCode("GROUP_TEST")
                .dataDate(TODAY)
                .version("V1")
                .status("COMPLETE")
                .dataClassification(classification)
                .sourceModes(Map.of("subjectValueMode", "EXCLUSIVE"))
                .memberOrgCodes(List.of("001", "002"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(2)
                        .receivedSubjects(2)
                        .selectedComplete(true)
                        .build())
                .metricContracts(Map.of("M_0001", BranchDashboardMetricContractDTO.builder()
                        .metricCode("M_0001")
                        .metricName("存款余额")
                        .unit("YUAN")
                        .build()))
                .rows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(TODAY)
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(100)))
                                .build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(TODAY)
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(200)))
                                .build()))
                .build();
    }

    private static ScreenQueryEngine engine(BranchDashboardBatchQueryApi batchApi) {
        ScreenQueryEngine engine = new ScreenQueryEngine(
                null,
                mock(OrgApi.class),
                List.of("ORG_INDEX_RESULT"),
                List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT"),
                1000);
        engine.setBatchQueryApi(batchApi);
        return engine;
    }

    private static ScreenDataReqDTO request() {
        ScreenDataReqDTO request = new ScreenDataReqDTO();
        request.setPeriod("LATEST");
        request.setNamedGroup(true);
        request.setServerOrgCodes(List.of("001", "002"));
        request.setServerGroupCode("GROUP_TEST");
        return request;
    }

    private static RptScreenDatasource datasource(String config) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setConfigJson(config);
        return datasource;
    }

    private static String config() {
        return "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"},"
                + "\"batchPolicy\":{\"requiredComplete\":true}}";
    }
}
