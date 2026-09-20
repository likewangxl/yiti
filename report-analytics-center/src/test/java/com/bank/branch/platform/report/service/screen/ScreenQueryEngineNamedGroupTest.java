package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.enums.RptErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 命名机构组本期只允许可证明机构范围的 ORG_INDEX_RESULT 宽表。 */
class ScreenQueryEngineNamedGroupTest {

    private final OrgApi orgApi = mock(OrgApi.class);
    private final ScreenQueryEngine engine = new ScreenQueryEngine(
            null, orgApi, List.of("ORG_INDEX_RESULT", "SYS_CONTROL", "PERF_KPI_SCORE",
                    "PERF_METRIC_DEF", "EXT_ORG_INFO"),
            List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE"), 1000);

    @BeforeEach
    void resetOrgApi() {
        org.mockito.Mockito.reset(orgApi);
    }

    @Test
    void wideTable_namedGroup_usesPreparedOrgInPredicate() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setPeriod("LATEST");
        req.setContextParams(Map.of("orgCode", "forged-client-org"));
        req.setNamedGroup(true);
        req.setServerOrgCodes(List.of("105", "128"));
        var query = engine.build("WIDE_TABLE",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                        + "\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"余额\",\"slot\":3}]}",
                req, 1000, LocalDate.of(2026, 8, 11));

        assertThat(query.sql()).contains("org_code IN (?, ?)");
        assertThat(query.sql()).doesNotContain("WHERE  AND");
        assertThat(query.sql()).doesNotContain("forged-client-org");
        assertThat(query.params()).containsExactly("105", "128");
    }

    @Test
    void customSql_namedGroupIsDeferredAndRejectedEvenWithLegacyMarker() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setPeriod("LATEST");
        req.setServerOrgCodes(List.of("105", "128"));
        req.setNamedGroup(true);
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\",\"subjectCol\":\"org_code\","
                        + "\"sql\":\"SELECT org_code FROM ORG_INDEX_RESULT WHERE /*#ORG_SCOPE*/"
                        + " AND data_date BETWEEN #{dateFrom} AND #{dateTo}\"}",
                req, 10, LocalDate.of(2026, 8, 11)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
    }

    @Test
    void namedGroupWideTableRejectsAnyTableOtherThanOrgIndexResult() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setServerOrgCodes(List.of("105"));
        req.setNamedGroup(true);

        assertThatThrownBy(() -> engine.build("WIDE_TABLE",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                        + "\"table\":\"EMP_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"余额\",\"slot\":3}]}",
                req, 10, LocalDate.of(2026, 8, 11)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
    }

    @Test
    void kpiDetailNamedGroup_snapshotUsesOutputSubsetAndCommonAuthorizedDate() {
        ScreenDataReqDTO req = namedGroupRequest(List.of("128", "129"), List.of("128", "129", "169"));
        req.setContextParams(Map.of("orgCode", "forged-client-org"));

        var query = engine.build("KPI_DETAIL",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                        + "\"schemeCode\":\"KPI0724\",\"subjectType\":\"ORG\","
                        + "\"mode\":\"SNAPSHOT\",\"metrics\":[{\"metricCode\":\"M0277\"}]}",
                req, 1000, LocalDate.of(2026, 9, 16));

        assertThat(query.sql()).contains("s.subject_id IN (?, ?)");
        assertThat(query.sql()).contains("MAX(common.data_date)");
        assertThat(query.sql()).contains("common.subject_id IN (?, ?, ?)");
        assertThat(query.sql()).contains("common.metric_code IN (?)");
        assertThat(query.sql()).contains("s.metric_code IN (?)");
        assertThat(query.sql()).contains("s.subject_id AS org_code");
        assertThat(query.sql()).contains("AS org_name");
        assertThat(query.sql()).contains("AS data_date");
        assertThat(query.sql()).contains("AS metric_name");
        assertThat(query.sql()).contains("AS attention_label")
                .contains("AS attention_count")
                .contains("s.target_value <= 0 THEN NULL")
                .contains("s.actual_value < s.target_value THEN 1 ELSE 0");
        assertThat(query.sql()).doesNotContain("forged-client-org");
        assertThat(query.params()).doesNotContain("forged-client-org");
        assertThat(query.params()).contains("128", "129", "128", "129", "169", "M0277");
    }

    @Test
    void kpiDetailNamedGroup_rejectsOutputCodesOutsideAuthorizedSubset() {
        ScreenDataReqDTO req = namedGroupRequest(List.of("130"), List.of("128", "129"));

        assertThatThrownBy(() -> engine.build("KPI_DETAIL", namedKpiSnapshotConfig(), req,
                1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }

    @Test
    void kpiDetailNamedGroup_emptyScopeFailsClosed() {
        ScreenDataReqDTO req = namedGroupRequest(List.of(), List.of("128", "129"));

        assertThatThrownBy(() -> engine.build("KPI_DETAIL", namedKpiSnapshotConfig(), req,
                1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    }

    @Test
    void kpiDetailNamedGroup_rejectsLegacySubjectAndTrendModes() {
        ScreenDataReqDTO req = namedGroupRequest(List.of("128"), List.of("128"));

        assertThatThrownBy(() -> engine.build("KPI_DETAIL", namedKpiSnapshotConfig()
                        .replace("\"subjectType\":\"ORG\"", "\"subjectType\":\"EMP\""),
                req, 1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", namedKpiSnapshotConfig()
                        .replace("\"mode\":\"SNAPSHOT\"", "\"mode\":\"TREND\""),
                req, 1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
    }

    @Test
    void namedGroup_kpiResultRemainsRejected() {
        ScreenDataReqDTO req = namedGroupRequest(List.of("128"), List.of("128"));

        assertThatThrownBy(() -> engine.build("KPI_RESULT",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\",\"cycleType\":\"MONTHLY\"}",
                req, 1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
    }

    @Test
    void m98NamedGroup_summaryBuildUsesFixedProfileTemplate() {
        ScreenDataReqDTO req = namedGroupRequest(List.of("330"), List.of("330", "90"));
        UniqueUserOrgDTO mapping = new UniqueUserOrgDTO();
        mapping.setEmpId("U330");
        mapping.setOrgCode("330");
        UniqueUserOrgDTO otherMapping = new UniqueUserOrgDTO();
        otherMapping.setEmpId("U90");
        otherMapping.setOrgCode("90");
        when(orgApi.listUniqueUserOrgs(List.of("330", "90")))
                .thenReturn(List.of(mapping, otherMapping));

        var query = engine.build("M98_STAT",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                        + "\"profile\":\"CORP_REVENUE\",\"mode\":\"SUMMARY\"}",
                req, 1000, LocalDate.of(2026, 9, 16));

        assertThat(query.sql()).contains("XAN_M98_EMP_STAT_SHOW3")
                .contains("CURRENCY_TYPE = '199'")
                .contains("EMP_ID IN (?)")
                .contains("STATIS_DT");
        assertThat(query.sql()).doesNotContain("#{")
                .doesNotContain("CURRENCY_TYPE = '01'");
        assertThat(query.params()).contains("U330", "U90");
    }

    @Test
    void m98NamedGroup_unknownProfileOrModeRemainsRejected() {
        ScreenDataReqDTO req = namedGroupRequest(List.of("330"), List.of("330"));

        assertThatThrownBy(() -> engine.build("M98_STAT",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                        + "\"profile\":\"CORP_LOAN\",\"mode\":\"SUMMARY\"}", req,
                1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        assertThatThrownBy(() -> engine.build("M98_STAT",
                "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                        + "\"profile\":\"CORP_REVENUE\",\"mode\":\"TREND\"}", req,
                1000, LocalDate.of(2026, 9, 16)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
    }

    @Test
    void kpiDetailNamedGroup_qualityReportsIntersectionWithoutFillingMissingSubjects() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        List<String> columns = List.of("org_code", "org_name", "data_date", "metric_code", "metric_name",
                "actual_value", "target_value", "completion_rate", "gap");
        List<List<Object>> rows = List.of(
                List.of("330", "机构330", "2026-09-15", "M0277", "经营指标", 0, 5000000, 0, 5000000),
                List.of("90", "机构90", "2026-09-15", "M0277", "经营指标", 0, 5000000, 0, 5000000));
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            when(metadata.getColumnLabel(i + 1)).thenReturn(columns.get(i));
        }
        Iterator<List<Object>> iterator = rows.iterator();
        List<Object>[] current = new List[]{null};
        when(resultSet.next()).thenAnswer(invocation -> {
            if (!iterator.hasNext()) {
                return false;
            }
            current[0] = iterator.next();
            return true;
        });
        when(resultSet.getObject(anyInt())).thenAnswer(invocation ->
                current[0].get(invocation.getArgument(0, Integer.class) - 1));

        ScreenQueryEngine queryEngine = new ScreenQueryEngine(dataSource, orgApi,
                List.of("PERF_KPI_SCORE", "PERF_METRIC_DEF", "EXT_ORG_INFO"),
                List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE"), 1000);
        ScreenDataReqDTO req = namedGroupRequest(List.of("330", "90"),
                List.of("330", "90", "169", "191", "126", "127", "130"));
        ScreenDataRespDTO response = queryEngine.queryAt(datasource(namedKpiSnapshotConfig()), req,
                LocalDate.of(2026, 9, 16), 1000);

        assertThat(response.getRows()).containsExactlyElementsOf(rows);
        assertThat(response.getRows()).noneMatch(row -> "128".equals(row.get(0)));
        assertThat(response.getQuality()).isNotNull();
        assertThat(response.getQuality().getExpectedSubjects()).isEqualTo(7);
        assertThat(response.getQuality().getReceivedSubjects()).isEqualTo(2);
        assertThat(response.getQuality().getDataDate()).isEqualTo("2026-09-15");
        assertThat(response.getQuality().getStatus()).isEqualTo("PARTIAL");
        verify(orgApi, never()).getOrgsByCodes(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyContextCustomSqlRemainsSupported() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setContextParams(Map.of("orgCode", "105"));
        var query = engine.build("CUSTOM_SQL",
                "{\"schemaVersion\":1,\"scopeMode\":\"LEGACY_CONTEXT\","
                        + "\"sql\":\"SELECT org_code FROM ORG_INDEX_RESULT WHERE org_code = #{orgCode}\"}",
                req, 10, LocalDate.of(2026, 8, 11));

        assertThat(query.sql()).contains("org_code = ?");
        assertThat(query.params()).containsExactly("105");
    }

    private static String namedKpiSnapshotConfig() {
        return "{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                + "\"schemeCode\":\"KPI0724\",\"subjectType\":\"ORG\",\"mode\":\"SNAPSHOT\","
                + "\"metrics\":[{\"metricCode\":\"M0277\"}]}";
    }

    private static ScreenDataReqDTO namedGroupRequest(List<String> outputCodes, List<String> authorizedCodes) {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setPeriod("LATEST");
        req.setNamedGroup(true);
        req.setServerOrgCodes(new ArrayList<>(outputCodes));
        req.setServerAuthorizedOrgCodes(new ArrayList<>(authorizedCodes));
        return req;
    }

    private static RptScreenDatasource datasource(String config) {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setSourceKind("KPI_DETAIL");
        ds.setConfigJson(config);
        return ds;
    }
}
