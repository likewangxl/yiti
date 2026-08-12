package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.enums.RptErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 命名机构组本期只允许可证明机构范围的 ORG_INDEX_RESULT 宽表。 */
class ScreenQueryEngineNamedGroupTest {

    private final ScreenQueryEngine engine = new ScreenQueryEngine(
            null,
            List.of("ORG_INDEX_RESULT", "SYS_CONTROL"),
            List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE"),
            1000);

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
}
