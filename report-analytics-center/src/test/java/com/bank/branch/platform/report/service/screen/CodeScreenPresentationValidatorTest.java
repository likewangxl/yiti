package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CODE 经营全景画布契约的纯单元测试；不启动 Spring，也不连接数据库。 */
class CodeScreenPresentationValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String style = "{\"presentation\":{\"type\":\"CODE\",\"template\":\"branch-overview-v1\"}}";
    private final String retailStyle = "{\"presentation\":{\"type\":\"CODE\",\"template\":\"retail-overview-v1\"}}";

    private String draft(String component, String id, String bindingKey, String bindJson) {
        return "{\"components\":[{\"component\":\"" + component + "\",\"id\":\""
                + id + "\",\"blockId\":12,\"propValue\":{\"bindingKey\":\"" + bindingKey
                + "\"},\"bindJson\":" + MAPPER.valueToTree(bindJson) + "}]}";
    }

    private String validBind(String fields, String units) {
        return "{\"dsId\":12,\"period\":\"LATEST\",\"fields\":" + fields
                + ",\"units\":" + units + "}";
    }

    private RptScreenBlock block(String bindJson) {
        RptScreenBlock block = new RptScreenBlock();
        block.setId(12L);
        block.setBindJson(bindJson);
        return block;
    }

    private RptScreenDatasource orgSubjectDatasource() {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(12L);
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\","
                + "\"metrics\":[{\"metricCode\":\"M1\",\"metricName\":\"存款余额\",\"slot\":1}],"
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"}}");
        return datasource;
    }

    private RptScreenDatasource compositionDatasource() {
        RptScreenDatasource datasource = orgSubjectDatasource();
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\","
                + "\"metrics\":[{\"metricCode\":\"CORP\",\"metricName\":\"对公余额\",\"slot\":1},"
                + "{\"metricCode\":\"RETAIL\",\"metricName\":\"零售余额\",\"slot\":2}],"
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"}}");
        return datasource;
    }

    private RptScreenDatasource customSqlCompositionDatasource() {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(12L);
        datasource.setSourceKind("CUSTOM_SQL");
        datasource.setConfigJson("{\"fieldMeta\":["
                + "{\"col\":\"corporate\",\"role\":\"METRIC\"},"
                + "{\"col\":\"retail\",\"role\":\"METRIC\"}]}");
        return datasource;
    }

    private RptScreenDatasource retailRankingDatasource() {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(12L);
        datasource.setBizLine("RETAIL");
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\","
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},"
                + "\"metrics\":[{\"metricCode\":\"AUM\",\"metricName\":\"aum\",\"slot\":1},"
                + "{\"metricCode\":\"INC\",\"metricName\":\"increase\",\"slot\":2},"
                + "{\"metricCode\":\"RATE\",\"metricName\":\"rate\",\"slot\":3},"
                + "{\"metricCode\":\"NPL\",\"metricName\":\"nplRate\",\"slot\":4}]}" );
        return datasource;
    }

    @Test
    void acceptsBranchOverviewWithRequiredDepositBinding() {
        org.assertj.core.api.Assertions.assertThat(CodeScreenPresentationValidator.isCodePresentation(style)).isTrue();
        String bind = validBind("{\"value\":\"存款余额\"}", "{\"value\":\"HUNDRED_MILLION\"}");
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit", "deposit", bind), List.of(block(bind)));
    }

    @Test
    void acceptsRetailOverviewWithRetailSlotsAndSharedBranches() throws Exception {
        org.assertj.core.api.Assertions.assertThat(CodeScreenPresentationValidator.isCodePresentation(retailStyle)).isTrue();

        String aum = validBind("{\"value\":\"零售AUM\",\"change\":\"AUM增幅\",\"date\":\"日期\"}",
                "{\"value\":\"YUAN\",\"change\":\"PERCENT\"}");
        String trend = "{\"dsId\":12,\"period\":\"LAST_6M_EOM\","
                + "\"fields\":{\"date\":\"日期\",\"aum\":\"零售AUM\",\"deposit\":\"零售存款\"},"
                + "\"units\":{\"aum\":\"TEN_THOUSAND\",\"deposit\":\"YUAN\"}}";
        String segments = validBind("{\"name\":\"客群\",\"customers\":\"客户数\",\"aum\":\"AUM\"}",
                "{\"customers\":\"COUNT\",\"aum\":\"HUNDRED_MILLION\"}");
        String ranking = validBind("{\"orgCode\":\"org_code\",\"name\":\"org_name\",\"aum\":\"aum\","
                        + "\"increase\":\"increase\",\"rate\":\"rate\",\"nplRate\":\"nplRate\"}",
                "{\"aum\":\"YUAN\",\"increase\":\"TEN_THOUSAND\",\"rate\":\"RATIO\",\"nplRate\":\"PERCENT\"}");
        String branches = validBind("{\"orgCode\":\"org_code\",\"deposit\":\"零售存款\"}",
                "{\"deposit\":\"YUAN\"}");

        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-aum", "retailAum", aum));
        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-trend", "retailTrend", trend));
        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-segments", "retailSegments", segments));
        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-ranking", "retailRanking", ranking));
        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-branches", "branches", branches));
        CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(ranking), "retailRanking", retailRankingDatasource());
    }

    @Test
    void branchAndRetailTemplatesCannotMixTheirSlotSets() {
        String branchBind = validBind("{\"value\":\"余额\"}", "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-branch-slot", "deposit", branchBind)))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String retailBind = validBind("{\"value\":\"零售AUM\"}", "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-retail-slot", "retailAum", retailBind)))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void retailSlotsEnforceRequiredShapeAndSemanticUnits() {
        String customers = validBind("{\"value\":\"高价值客户\"}", "{\"value\":\"TEN_THOUSAND_COUNT\"}");
        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-customers", "retailValueCustomers", customers));

        String npl = validBind("{\"value\":\"不良率\",\"change\":\"变化\",\"date\":\"日期\"}",
                "{\"value\":\"RATIO\",\"change\":\"PERCENT\"}");
        CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-npl", "retailNplRate", npl));

        String trendWithoutRequiredMetric = validBind("{\"date\":\"日期\",\"revenue\":\"收入\"}",
                "{\"revenue\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-trend-invalid", "retailTrend", trendWithoutRequiredMetric)))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String incompleteSegments = validBind("{\"name\":\"客群\",\"customers\":\"客户数\"}",
                "{\"customers\":\"COUNT\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-segments-invalid", "retailSegments", incompleteSegments)))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String attentionWithTenThousandCount = validBind("{\"label\":\"事项\",\"count\":\"数量\"}",
                "{\"count\":\"TEN_THOUSAND_COUNT\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(retailStyle,
                draft("ChartWidget", "w-attention-invalid", "retailAttention", attentionWithTenThousandCount)))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void retailRankingNamedGroupMustUseOrgSubjectIdentity() throws Exception {
        String valid = validBind("{\"orgCode\":\"org_code\",\"name\":\"org_name\",\"aum\":\"aum\"}",
                "{\"aum\":\"YUAN\"}");
        CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(valid), "retailRanking", retailRankingDatasource());

        String forged = validBind("{\"orgCode\":\"org_name\",\"name\":\"org_name\",\"aum\":\"aum\"}",
                "{\"aum\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(forged), "retailRanking", retailRankingDatasource()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void legacyCanvasStyleSerializationOmitsAbsentPresentation() {
        com.fasterxml.jackson.databind.JsonNode serialized = MAPPER.valueToTree(new CanvasStyleDTO());
        org.assertj.core.api.Assertions.assertThat(serialized.has("presentation")).isFalse();
        org.assertj.core.api.Assertions.assertThat(CodeScreenPresentationValidator
                .isCodePresentation(serialized.toString())).isFalse();
    }

    @Test
    void acceptsSourcePresentationMetadataForCodeAndLegacyCanvasStyles() {
        String metadata = "\"dataNotice\":\"系统联调数据：当前指标结果含测试计算\","
                + "\"metricLabels\":{\"deposit\":\"一般性存款余额\",\"loan\":\"对公一般性贷款余额\"}";
        CodeScreenPresentationValidator.validateCanvasStyle("{" + metadata + "}");
        CodeScreenPresentationValidator.validateCanvasStyle("{" + metadata + ","
                + "\"presentation\":{\"type\":\"CODE\",\"template\":\"branch-overview-v1\"}}");
    }

    @Test
    void rejectsInvalidSourceNoticeLengthMarkupAndMetricLabelKey() {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateCanvasStyle(
                "{\"dataNotice\":\"<script>alert(1)</script>\"}"))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateCanvasStyle(
                "{\"dataNotice\":\"" + "x".repeat(241) + "\"}"))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateCanvasStyle(
                "{\"metricLabels\":{\"unknown\":\"不在白名单\"}}"))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateCanvasStyle(
                "{\"metricLabels\":{\"deposit\":\"<b>余额</b>\"}}"))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void rejectsUnknownPresentationType() {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(
                style.replace("\"CODE\"", "\"UNKNOWN\""),
                draft("ChartWidget", "w-deposit", "deposit",
                        validBind("{\"value\":\"存款余额\"}", "{\"value\":\"YUAN\"}"))))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void rejectsUnknownPresentationTemplate() {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(
                style.replace("branch-overview-v1", "unknown-v1"),
                draft("ChartWidget", "w-deposit", "deposit",
                        validBind("{\"value\":\"存款余额\"}", "{\"value\":\"YUAN\"}"))))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void stringCodePresentationRecognizesValidAndRejectsUnknownDeclarations() {
        org.assertj.core.api.Assertions.assertThat(CodeScreenPresentationValidator
                .isCodePresentation(style)).isTrue();
        assertThatThrownBy(() -> CodeScreenPresentationValidator.isCodePresentation(
                style.replace("branch-overview-v1", "unknown-v1")))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void rejectsNonChartNodeAndDuplicateBindingKey() {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                "{\"components\":[{\"component\":\"TextLabel\",\"id\":\"x\"}]}", List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String bind = validBind("{\"value\":\"存款余额\"}", "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                "{\"components\":["
                        + "{\"component\":\"ChartWidget\",\"id\":\"a\",\"propValue\":{\"bindingKey\":\"deposit\"},\"bindJson\":" + MAPPER.valueToTree(bind) + "},"
                        + "{\"component\":\"ChartWidget\",\"id\":\"b\",\"propValue\":{\"bindingKey\":\"deposit\"},\"bindJson\":" + MAPPER.valueToTree(bind) + "}]}" , List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void rejectsInvalidDsIdFieldsAndUnit() {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w", "deposit",
                        "{\"dsId\":0,\"period\":\"LATEST\",\"fields\":{},\"units\":{}}"), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w", "deposit",
                        "{\"dsId\":12,\"period\":\"LATEST\",\"fields\":{\"value\":\"\"},\"units\":{\"value\":\"BAD\"}}"), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void trendNeedsDateAndAtLeastOneMetric() {
        String noMetric = validBind("{\"date\":\"月份\"}", "{\"date\":\"COUNT\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-trend", "trend", noMetric), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String validTrend = "{\"dsId\":12,\"period\":\"LAST_6M_EOM\","
                + "\"fields\":{\"date\":\"月份\",\"deposit\":\"余额\"},"
                + "\"units\":{\"deposit\":\"YUAN\"}}";
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-trend-valid", "trend", validTrend));

        String illegalPeriod = validTrend.replace("LAST_6M_EOM", "RANGE");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-trend-range", "trend", illegalPeriod), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void acceptsNewDepositSlotsAndDepositIncreaseOnlyTrend() {
        String increase = validBind("{\"value\":\"存款净增\"}", "{\"value\":\"YUAN\"}");
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit-increase", "depositIncrease", increase));

        String average = validBind("{\"value\":\"存款月均\"}", "{\"value\":\"HUNDRED_MILLION\"}");
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit-average", "depositAverage", average));

        String trend = "{\"dsId\":12,\"period\":\"LAST_6M_EOM\","
                + "\"fields\":{\"date\":\"月份\",\"depositIncrease\":\"净增\"},"
                + "\"units\":{\"depositIncrease\":\"TEN_THOUSAND\"}}";
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-trend-increase", "trend", trend));
    }

    @Test
    void rankingAcceptsOptionalIncreaseAndAverageAmountFields() {
        String ranking = "{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"orgCode\":\"机构号\",\"name\":\"机构名称\","
                + "\"value\":\"存款余额\",\"increase\":\"存款净增\",\"average\":\"存款月均\"},"
                + "\"units\":{\"value\":\"YUAN\",\"increase\":\"TEN_THOUSAND\","
                + "\"average\":\"HUNDRED_MILLION\"}}";
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-ranking", "ranking", ranking));
    }

    @Test
    void compositionAcceptsLegacyRowsAndWideTableColumns() throws Exception {
        String rows = validBind("{\"name\":\"org_name\",\"value\":\"对公余额\"}",
                "{\"value\":\"YUAN\"}");
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-rows", "composition", rows));

        String columns = validBind("{\"corporate\":\"对公余额\",\"retail\":\"零售余额\"}",
                "{\"corporate\":\"TEN_THOUSAND\",\"retail\":\"YUAN\"}");
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-columns", "composition", columns));
        CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(columns), "composition", compositionDatasource());
    }

    @Test
    void compositionRejectsMixedShapeIncompleteUnitsAndMixedUnitKinds() {
        String mixedShape = validBind(
                "{\"name\":\"org_name\",\"value\":\"对公余额\",\"corporate\":\"对公余额\",\"retail\":\"零售余额\"}",
                "{\"value\":\"YUAN\",\"corporate\":\"YUAN\",\"retail\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-mixed", "composition", mixedShape), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String missingRetail = validBind("{\"corporate\":\"对公余额\"}",
                "{\"corporate\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-missing", "composition", missingRetail), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String mixedKinds = validBind("{\"corporate\":\"对公余额\",\"retail\":\"零售余额\"}",
                "{\"corporate\":\"PERCENT\",\"retail\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-kinds", "composition", mixedKinds), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String unboundUnit = validBind("{\"corporate\":\"对公余额\",\"retail\":\"零售余额\"}",
                "{\"corporate\":\"YUAN\",\"retail\":\"YUAN\",\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-unit", "composition", unboundUnit), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String missingUnits = validBind("{\"corporate\":\"对公余额\",\"retail\":\"零售余额\"}", "{}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-composition-missing-units", "composition", missingUnits), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void compositionRequiresMetricOutputColumnsAndPublishedSnapshotRoundTrip() throws Exception {
        RptScreenDatasource datasource = compositionDatasource();
        String dimensionAsMetric = validBind("{\"corporate\":\"org_name\",\"retail\":\"零售余额\"}",
                "{\"corporate\":\"YUAN\",\"retail\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(dimensionAsMetric), "composition", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String customSqlColumns = validBind("{\"corporate\":\"corporate\",\"retail\":\"retail\"}",
                "{\"corporate\":\"YUAN\",\"retail\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(customSqlColumns), "composition", customSqlCompositionDatasource()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String columns = validBind("{\"corporate\":\"对公余额\",\"retail\":\"零售余额\"}",
                "{\"corporate\":\"PERCENT\",\"retail\":\"RATIO\"}");
        String component = "{\"component\":\"ChartWidget\",\"id\":\"w-composition-published\","
                + "\"blockId\":12,\"propValue\":{\"bindingKey\":\"composition\"},\"bindJson\":"
                + MAPPER.valueToTree(columns) + "}";
        String published = "{\"canvasStyle\":{\"presentation\":{\"type\":\"CODE\",\"template\":\"branch-overview-v1\"}},"
                + "\"components\":[" + component + "],\"bindSnapshots\":{\"12\":{\"bind\":"
                + MAPPER.readTree(columns) + "}}}";
        CodeScreenPresentationValidator.validatePublishedPackage(published);
    }

    @Test
    void rejectsUnitsThatDoNotMatchSemanticField() {
        String amountAsRatio = validBind("{\"value\":\"存款余额\"}", "{\"value\":\"PERCENT\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit", "deposit", amountAsRatio), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String attentionCountAsRatio = validBind("{\"label\":\"事项\",\"count\":\"数量\"}",
                "{\"count\":\"RATIO\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-attention", "attention", attentionCountAsRatio), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String trend = validBind("{\"date\":\"月份\",\"deposit\":\"余额\"}",
                "{\"deposit\":\"COUNT\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-trend", "trend", trend), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String missingUnit = validBind("{\"value\":\"存款余额\"}", "{}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit-no-unit", "deposit", missingUnit), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String invalidNewAmountUnit = validBind("{\"value\":\"存款净增\"}", "{\"value\":\"COUNT\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit-increase-bad-unit", "depositIncrease", invalidNewAmountUnit), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String rankingWrongUnit = "{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"orgCode\":\"机构号\",\"name\":\"机构名称\",\"value\":\"存款余额\","
                + "\"increase\":\"存款净增\"},\"units\":{\"value\":\"YUAN\",\"increase\":\"PERCENT\"}}";
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-ranking-bad-unit", "ranking", rankingWrongUnit), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void citySummaryNeedsAtLeastOneMetric() {
        String noMetric = validBind("{\"cityCode\":\"CITY\"}", "{}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-city", "citySummary", noMetric), List.of()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void citySummaryMayUseOrgCodeIdentityForOrgSubjectDatasource() throws Exception {
        String bind = validBind("{\"orgCode\":\"org_code\",\"deposit\":\"存款余额\"}",
                "{\"deposit\":\"YUAN\"}");
        CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(bind), "citySummary", orgSubjectDatasource());
    }

    @Test
    void rejectsUnknownDatasourceColumnAndWrongDimensionMetricRole() throws Exception {
        RptScreenDatasource datasource = orgSubjectDatasource();
        String unknown = validBind("{\"value\":\"does_not_exist\"}", "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(unknown), "deposit", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String dimensionAsMetric = validBind("{\"value\":\"org_code\"}", "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(dimensionAsMetric), "deposit", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String metricAsDate = validBind("{\"date\":\"存款余额\",\"deposit\":\"存款余额\"}",
                "{\"deposit\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(metricAsDate), "trend", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String newMetricAsDimension = validBind("{\"date\":\"org_code\",\"depositIncrease\":\"org_name\"}",
                "{\"depositIncrease\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(newMetricAsDimension), "trend", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String depositIncreaseValueAsDimension = validBind("{\"value\":\"org_code\"}",
                "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(depositIncreaseValueAsDimension), "depositIncrease", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String depositAverageValueAsDimension = validBind("{\"value\":\"org_name\"}",
                "{\"value\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(depositAverageValueAsDimension), "depositAverage", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String rankingIncreaseAsDimension = validBind(
                "{\"orgCode\":\"org_code\",\"name\":\"org_name\","
                        + "\"value\":\"存款余额\",\"increase\":\"org_code\"}",
                "{\"value\":\"YUAN\",\"increase\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(rankingIncreaseAsDimension), "ranking", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());

        String rankingAverageAsDimension = validBind(
                "{\"orgCode\":\"org_code\",\"name\":\"org_name\","
                        + "\"value\":\"存款余额\",\"average\":\"org_name\"}",
                "{\"value\":\"YUAN\",\"average\":\"YUAN\"}");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateBindAgainstDatasource(
                MAPPER.readTree(rankingAverageAsDimension), "ranking", datasource))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }
}
