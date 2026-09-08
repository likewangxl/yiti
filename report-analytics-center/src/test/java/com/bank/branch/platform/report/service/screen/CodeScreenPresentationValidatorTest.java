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

    @Test
    void acceptsBranchOverviewWithRequiredDepositBinding() {
        org.assertj.core.api.Assertions.assertThat(CodeScreenPresentationValidator.isCodePresentation(style)).isTrue();
        String bind = validBind("{\"value\":\"存款余额\"}", "{\"value\":\"HUNDRED_MILLION\"}");
        CodeScreenPresentationValidator.validateDraft(style,
                draft("ChartWidget", "w-deposit", "deposit", bind), List.of(block(bind)));
    }

    @Test
    void legacyCanvasStyleSerializationOmitsAbsentPresentation() {
        com.fasterxml.jackson.databind.JsonNode serialized = MAPPER.valueToTree(new CanvasStyleDTO());
        org.assertj.core.api.Assertions.assertThat(serialized.has("presentation")).isFalse();
        org.assertj.core.api.Assertions.assertThat(CodeScreenPresentationValidator
                .isCodePresentation(serialized.toString())).isFalse();
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
