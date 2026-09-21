package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreeReportScreenQueryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BATCH = "TEST_BRANCH_OPERATING_20260921";
    private static final String CONFIG = "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
            + "\"dataClassification\":\"TEST\",\"profile\":\"BRANCH_OVERVIEW\","
            + "\"batchId\":\"" + BATCH + "\"}";

    @Test
    void planUsesFixedBatchAndServerOutputSubsetAndLimitPlusOne() throws Exception {
        ScreenQueryEngine engine = engine(null);
        ScreenDataReqDTO request = request(List.of("001", "002"), List.of("002"));
        request.setBatchId("ATTACKER_BATCH");

        var query = engine.build("FREE_REPORT", CONFIG, request, 10, java.time.LocalDate.of(2026, 9, 21));

        assertThat(query.sql()).contains("JOIN RPT_FREE_REPORT_BATCH b ON b.ID = r.BATCH_ID")
                .contains("b.STATUS = 'SUCCESS'")
                .contains("r.ORG_CODE IN (?)")
                .contains("ORDER BY r.ID LIMIT 11")
                .doesNotContain("EMP_ID", "COL_1", "COL_2", "JSON_EXTRACT");
        assertThat(query.params()).containsExactly(BATCH, "002");
    }

    @Test
    void missingOrExpandedNamedGroupRangeFailsBeforeJdbc() {
        ScreenQueryEngine engine = engine(null);
        ScreenDataReqDTO empty = request(List.of(), List.of());
        assertThatThrownBy(() -> engine.build("FREE_REPORT", CONFIG, empty, 10,
                java.time.LocalDate.of(2026, 9, 21)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43015");

        ScreenDataReqDTO expanded = request(List.of("001"), List.of("002"));
        assertThatThrownBy(() -> engine.build("FREE_REPORT", CONFIG, expanded, 10,
                java.time.LocalDate.of(2026, 9, 21)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43015");

        ScreenDataReqDTO requestedOutside = request(List.of("001"), List.of("001"));
        requestedOutside.setServerRequestedOrgCode("999");
        assertThatThrownBy(() -> engine.build("FREE_REPORT", CONFIG, requestedOutside, 10,
                java.time.LocalDate.of(2026, 9, 21)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43015");
    }

    @Test
    void queryProjectsFixedColumnsAndMarksTestQuality() throws Exception {
        String json = validPayload();
        String defs = MAPPER.writeValueAsString(FreeReportScreenPolicy.JSON_FIELDS.stream()
                .map(key -> Map.of("key", key, "label", key)).toList());
        JdbcFixture fixture = jdbc(
                new String[]{"SUCCESS", "TEST_BRANCH_OPERATING_20260921_01", defs},
                List.of(List.of(101L, "002", json)));

        ScreenQueryEngine engine = engine(fixture.dataSource());
        ScreenDataRespDTO response = engine.queryAt(datasource(), request(List.of("001", "002"), List.of("002")),
                java.time.LocalDate.of(2026, 9, 21), 10);

        assertThat(response.getColumns()).containsExactlyElementsOf(FreeReportScreenPolicy.OUTPUT_COLUMNS);
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0).get(0)).isEqualTo("002");
        assertThat(response.getColumnsMeta()).extracting(ScreenDataRespDTO.ColumnMeta::getCol)
                .containsExactlyElementsOf(FreeReportScreenPolicy.OUTPUT_COLUMNS);
        assertThat(response.getColumnsMeta().get(11).getUnit()).isEqualTo("YUAN");
        assertThat(response.getColumnsMeta().get(8).getUnit()).isNull();
        assertThat(response.getQuality().getDataClassification()).isEqualTo("TEST");
        assertThat(response.getQuality().getVersion()).isEqualTo(BATCH);
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
        assertThat(response.getQuality().getDataDate()).isEqualTo("2026-09-20");
        verify(fixture.rowStatement()).setString(1, BATCH);
    }

    @Test
    void unknownPayloadAndNonNumericMetricAreRejected() throws Exception {
        String defs = MAPPER.writeValueAsString(FreeReportScreenPolicy.JSON_FIELDS.stream()
                .map(key -> Map.of("key", key, "label", key)).toList());
        Map<String, Object> unknown = MAPPER.readValue(validPayload(), Map.class);
        unknown.put("sql", 1);
        JdbcFixture unknownFixture = jdbc(
                new String[]{"SUCCESS", "TEST_BRANCH_OPERATING_20260921_01", defs},
                List.of(List.of(101L, "002", MAPPER.writeValueAsString(unknown))));
        assertThatThrownBy(() -> engine(unknownFixture.dataSource()).queryAt(datasource(),
                request(List.of("002"), List.of("002")), java.time.LocalDate.of(2026, 9, 21), 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");

        Map<String, Object> nonNumeric = MAPPER.readValue(validPayload(), Map.class);
        nonNumeric.put("count", "12");
        JdbcFixture numericFixture = jdbc(
                new String[]{"SUCCESS", "TEST_BRANCH_OPERATING_20260921_01", defs},
                List.of(List.of(102L, "002", MAPPER.writeValueAsString(nonNumeric))));
        assertThatThrownBy(() -> engine(numericFixture.dataSource()).queryAt(datasource(),
                request(List.of("002"), List.of("002")), java.time.LocalDate.of(2026, 9, 21), 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void moreThanLimitIsRejectedInsteadOfSilentlyTruncated() throws Exception {
        String defs = MAPPER.writeValueAsString(FreeReportScreenPolicy.JSON_FIELDS.stream()
                .map(key -> Map.of("key", key, "label", key)).toList());
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(List.of((long) i + 1, "002", validPayload()));
        }
        JdbcFixture fixture = jdbc(new String[]{"SUCCESS", "TEST_BRANCH_OPERATING_20260921_01", defs}, rows);
        assertThatThrownBy(() -> engine(fixture.dataSource()).queryAt(datasource(),
                request(List.of("002"), List.of("002")), java.time.LocalDate.of(2026, 9, 21), 2))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
    }

    private static String validPayload() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : FreeReportScreenPolicy.JSON_FIELDS) {
            values.put(key, null);
        }
        values.put("kind", "kpi");
        values.put("key", "deposit");
        values.put("name", "存款");
        values.put("data_date", "2026-09-20");
        values.put("unit", "YUAN");
        values.put("status", "ACTIVE");
        values.put("owner", "test");
        values.put("actual", 100);
        values.put("count", 3);
        return MAPPER.writeValueAsString(values);
    }

    private static RptScreenDatasource datasource() {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setSourceKind("FREE_REPORT");
        datasource.setConfigJson(CONFIG);
        datasource.setDsType("SINGLE");
        datasource.setBizLine("COMMON");
        return datasource;
    }

    private static ScreenDataReqDTO request(List<String> authorized, List<String> output) {
        ScreenDataReqDTO request = new ScreenDataReqDTO();
        request.setNamedGroup(true);
        request.setServerAuthorizedOrgCodes(authorized);
        request.setServerOrgCodes(output);
        return request;
    }

    private static ScreenQueryEngine engine(DataSource dataSource) {
        return new ScreenQueryEngine(dataSource, mock(OrgApi.class),
                List.of("EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT", "KPI_RESULT",
                        "SYS_CONTROL", "EXT_ORG_INFO", "ACT_RU_TASK"),
                List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT"),
                1000);
    }

    private static JdbcFixture jdbc(String[] batch, List<List<Object>> rows) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement batchStatement = mock(PreparedStatement.class);
        PreparedStatement rowStatement = mock(PreparedStatement.class);
        ResultSet batchResult = mock(ResultSet.class);
        ResultSet rowResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(batchStatement, rowStatement);
        when(batchStatement.executeQuery()).thenReturn(batchResult);
        when(rowStatement.executeQuery()).thenReturn(rowResult);
        when(batchResult.next()).thenReturn(true, false);
        when(batchResult.getString(1)).thenReturn(batch[0]);
        when(batchResult.getString(2)).thenReturn(batch[1]);
        when(batchResult.getString(3)).thenReturn(batch[2]);
        Iterator<List<Object>> iterator = rows.iterator();
        List<Object>[] current = new List[]{null};
        when(rowResult.next()).thenAnswer(invocation -> {
            if (!iterator.hasNext()) {
                return false;
            }
            current[0] = iterator.next();
            return true;
        });
        when(rowResult.getLong(any(Integer.class))).thenAnswer(invocation ->
                ((Number) current[0].get(invocation.getArgument(0, Integer.class) - 1)).longValue());
        when(rowResult.getString(any(Integer.class))).thenAnswer(invocation ->
                String.valueOf(current[0].get(invocation.getArgument(0, Integer.class) - 1)));
        return new JdbcFixture(dataSource, rowStatement);
    }

    private record JdbcFixture(DataSource dataSource, PreparedStatement rowStatement) {
    }
}
