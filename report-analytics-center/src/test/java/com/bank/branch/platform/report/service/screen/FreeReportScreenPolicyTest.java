package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreeReportScreenPolicyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void strictConfigAndFixedProjectionAreExposed() throws Exception {
        var config = MAPPER.readTree("{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                + "\"dataClassification\":\"TEST\",\"profile\":\"BRANCH_OVERVIEW\","
                + "\"batchId\":\"TEST_BRANCH_OPERATING_20260921\"}");

        FreeReportScreenPolicy.validateConfig(config);

        assertThat(FreeReportScreenPolicy.OUTPUT_COLUMNS).containsExactly(
                "org_code", "kind", "key", "name", "data_date", "unit", "status", "owner",
                "value", "yoy", "mom", "actual", "target", "rate", "gap", "deposit", "loan",
                "amount", "increase", "count", "pending", "days");
        assertThat(FreeReportScreenPolicy.JSON_FIELDS).containsExactlyElementsOf(
                FreeReportScreenPolicy.OUTPUT_COLUMNS.subList(1, FreeReportScreenPolicy.OUTPUT_COLUMNS.size()));
    }

    @Test
    void freeFormConfigAndUnknownPayloadAreRejected() throws Exception {
        var config = MAPPER.readTree("{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                + "\"dataClassification\":\"TEST\",\"profile\":\"BRANCH_OVERVIEW\","
                + "\"batchId\":\"TEST_BRANCH_OPERATING_20260921\",\"sql\":\"SELECT 1\"}");
        assertThatThrownBy(() -> FreeReportScreenPolicy.validateConfig(config))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");

        assertThatThrownBy(() -> FreeReportScreenPolicy.projectRow("610100",
                "{\"kind\":\"kpi\",\"unknown\":1}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void freeReportCodeCanvasMustDeclareTestClassification() throws Exception {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setSourceKind("FREE_REPORT");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDatasourceClassification(
                MAPPER.readTree("{\"dataClassification\":\"PROD\"}"), datasource))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
        CodeScreenPresentationValidator.validateDatasourceClassification(
                MAPPER.readTree("{\"dataClassification\":\"TEST\"}"), datasource);
    }
}
