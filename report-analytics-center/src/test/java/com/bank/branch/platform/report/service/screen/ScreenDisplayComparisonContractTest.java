package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayComparisonDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayContractValidator;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayPayloadDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayUnit;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 比较数据配置的纯契约测试；不启动 Spring，也不连接数据库。 */
class ScreenDisplayComparisonContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void comparisonDtoRoundTripsTypedMapAndDisabledEntryContainsOnlyEnabled() throws Exception {
        ScreenDisplayComparisonDTO comparison = new ScreenDisplayComparisonDTO();
        comparison.setEnabled(false);
        ScreenDisplayPayloadDTO payload = new ScreenDisplayPayloadDTO();
        payload.setComponents(List.of(component("deposit-card", "METRIC_CARD")));
        payload.setComparisons(Map.of("deposit-card", comparison));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(payload));

        assertThat(json.path("comparisons").path("deposit-card").fieldNames()).toIterable()
                .containsExactly("enabled");
        ScreenDisplayPayloadDTO roundTrip = MAPPER.treeToValue(json, ScreenDisplayPayloadDTO.class);
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1", roundTrip))
                .isEmpty();
    }

    @Test
    void beanValidationAllowsMinimalDisabledEntryAndCrossFieldValidatorRequiresUnitWhenEnabled() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        ScreenDisplayComparisonDTO disabled = comparison(false, null, null, null, null);
        assertThat(validator.validate(disabled)).isEmpty();

        ScreenDisplayComparisonDTO enabledWithoutUnit = comparison(true, 57L,
                List.of("deposit"), "data_date", null);
        assertThat(validator.validate(enabledWithoutUnit)).isEmpty();
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1",
                payload(enabledWithoutUnit, "deposit-card", "METRIC_CARD"))).isNotEmpty();
    }

    @Test
    void validatesEnabledComparisonShapeAndAmountTotalsButRejectsRatioOrOrdinaryCardSums() {
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1",
                payload(comparison(true, 57L, List.of("deposit_a", "deposit_b"), "data_date",
                        ScreenDisplayUnit.YUAN), "overview-deposit", "METRIC_CARD"))).isEmpty();

        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1",
                payload(comparison(true, 57L, List.of("deposit_a", "deposit_b"), "data_date",
                        ScreenDisplayUnit.PERCENT), "overview-deposit", "METRIC_CARD")))
                .isNotEmpty();
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1",
                payload(comparison(true, 57L, List.of("deposit_a", "deposit_b"), "data_date",
                        ScreenDisplayUnit.YUAN), "deposit-card", "METRIC_CARD")))
                .isNotEmpty();

        ScreenDisplayPayloadDTO percentageCard = payloadWithKey(
                comparison(true, 57L, List.of("completion_rate"), "data_date", ScreenDisplayUnit.YUAN),
                "completion-card", "completion-card", "METRIC_CARD");
        percentageCard.getComponents().get(0).getFormat().setDisplayUnit(ScreenDisplayUnit.PERCENT);
        percentageCard.getComponents().get(0).getDataRefs().get(0).setUnit(ScreenDisplayUnit.PERCENT);
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1", percentageCard))
                .isNotEmpty();
    }

    @Test
    void rejectsUnknownComparisonKeysAndOverviewKeysOnNonBranchTemplates() {
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("branch-overview-v1",
                payloadWithKey(comparison(false, null, null, null, null), "unknown-card", "deposit-card", "METRIC_CARD")))
                .isNotEmpty();
        assertThat(ScreenDisplayContractValidator.validateDisplayPayload("retail-overview-v1",
                payloadWithKey(comparison(false, null, null, null, null), "overview-deposit", "deposit-card", "METRIC_CARD")))
                .isNotEmpty();
    }

    @Test
    void strictCanvasJsonRejectsUnknownNullAndDuplicateComparisonFields() {
        String base = validStyle("{\"deposit-card\":{\"enabled\":true,\"historyBlockId\":57,"
                + "\"valueFields\":[\"deposit\"],\"dateField\":\"data_date\",\"sourceUnit\":\"YUAN\"}}");

        assertInvalid(base.replace("\"sourceUnit\":\"YUAN\"", "\"sourceUnit\":null"));
        assertInvalid(base.replace("\"enabled\":true", "\"enabled\":\"true\""));
        assertInvalid(base.replace("\"deposit\"],", "1],"));
        assertInvalid(base.replace("\"enabled\":true", "\"enabled\":true,\"unexpected\":1"));
        assertInvalid(base.replace("\"enabled\":true", "\"enabled\":true,\"enabled\":false"));
        assertInvalid(base.replace("\"display\":{", "\"comparisons\":{},\"display\":{"));
        assertInvalid(validStyle("{\"deposit-card\":{\"enabled\":false,\"dateField\":\"data_date\"}}"));
    }

    @Test
    void enabledComparisonHistoryMustBeCurrentLineTrendChartWidget() {
        String style = validStyle("{\"deposit-card\":{\"enabled\":true,\"historyBlockId\":57,"
                + "\"valueFields\":[\"deposit\"],\"dateField\":\"data_date\",\"sourceUnit\":\"YUAN\"}}");
        String draft = draft(57L, "LINE_TREND");

        CodeScreenPresentationValidator.validateDraft(style, draft,
                List.of(block(12L, "METRIC_CARD"), block(57L, "LINE_TREND")));

        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft(57L, "BAR_COMPARE"),
                List.of(block(12L, "METRIC_CARD"), block(57L, "BAR_COMPARE"))))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft(57L, "line_trend"),
                List.of(block(12L, "METRIC_CARD"), block(57L, "LINE_TREND"))))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(style,
                draft(99L, "LINE_TREND"),
                List.of(block(12L, "METRIC_CARD"), block(57L, "LINE_TREND"))))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void publishedPackageRechecksComparisonHistoryTypeFromTrustedSnapshot() {
        String style = validStyle("{\"deposit-card\":{\"enabled\":true,\"historyBlockId\":57,"
                + "\"valueFields\":[\"deposit\"],\"dateField\":\"data_date\",\"sourceUnit\":\"YUAN\"}}");
        String components = "[{\"component\":\"ChartWidget\",\"id\":\"primary\",\"blockId\":12,"
                + "\"innerType\":\"METRIC_CARD\",\"propValue\":{\"bindingKey\":\"deposit\"},"
                + "\"bindJson\":\"{\\\"dsId\\\":12,\\\"period\\\":\\\"LATEST\\\","
                + "\\\"fields\\\":{\\\"value\\\":\\\"balance\\\"},\\\"units\\\":{\\\"value\\\":\\\"YUAN\\\"}}\"},"
                + "{\"component\":\"ChartWidget\",\"id\":\"history\",\"blockId\":57,"
                + "\"innerType\":\"LINE_TREND\",\"propValue\":{\"bindingKey\":\"trend\"},"
                + "\"bindJson\":\"{\\\"dsId\\\":13,\\\"period\\\":\\\"LAST_10D\\\","
                + "\\\"fields\\\":{\\\"date\\\":\\\"data_date\\\",\\\"deposit\\\":\\\"balance\\\"},"
                + "\\\"units\\\":{\\\"deposit\\\":\\\"YUAN\\\"}}\"}]";
        String snapshots = "{\"12\":{\"componentType\":\"METRIC_CARD\",\"bind\":{\"dsId\":12,"
                + "\"period\":\"LATEST\",\"fields\":{\"value\":\"balance\"},"
                + "\"units\":{\"value\":\"YUAN\"}}},\"57\":{\"componentType\":\"LINE_TREND\","
                + "\"bind\":{\"dsId\":13,\"period\":\"LAST_10D\",\"fields\":{\"date\":\"data_date\","
                + "\"deposit\":\"balance\"},\"units\":{\"deposit\":\"YUAN\"}}}}";
        JsonNode packageNode;
        try {
            var root = MAPPER.createObjectNode();
            root.set("canvasStyle", MAPPER.readTree(style));
            root.set("components", MAPPER.readTree(components));
            root.set("bindSnapshots", MAPPER.readTree(snapshots));
            packageNode = root;
            assertThat(packageNode.path("components").isArray()).isTrue();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        CodeScreenPresentationValidator.validatePublishedPackage(packageNode);
        ((com.fasterxml.jackson.databind.node.ObjectNode) packageNode.path("bindSnapshots").path("57"))
                .put("componentType", "BAR_COMPARE");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validatePublishedPackage(packageNode))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
    }

    private ScreenDisplayPayloadDTO payload(ScreenDisplayComparisonDTO comparison, String key,
                                            String componentType) {
        return payloadWithKey(comparison, key, key, componentType);
    }

    private ScreenDisplayPayloadDTO payloadWithKey(ScreenDisplayComparisonDTO comparison, String comparisonKey,
                                                  String componentId, String componentType) {
        ScreenDisplayPayloadDTO payload = new ScreenDisplayPayloadDTO();
        payload.setComponents(List.of(component(componentId, componentType)));
        payload.setComparisons(Map.of(comparisonKey, comparison));
        return payload;
    }

    private com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayComponentDTO component(
            String id, String type) {
        var component = new com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayComponentDTO();
        component.setComponentId(id);
        component.setComponentType(com.bank.branch.platform.report.dto.req.presentation.ScreenComponentType.valueOf(type));
        component.setLayoutRegion(com.bank.branch.platform.report.dto.req.presentation.ScreenLayoutRegion.LEFT);
        component.setOrder(0);
        component.setVisible(true);
        var text = new com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayTextDTO();
        text.setTitleMode(com.bank.branch.platform.report.dto.req.presentation.ScreenTitleMode.AUTO);
        component.setText(text);
        var format = new com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayFormatDTO();
        format.setDisplayUnit(ScreenDisplayUnit.YUAN);
        component.setFormat(format);
        var content = new com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayContentDTO();
        content.setMainField("value");
        component.setContent(content);
        var interaction = new com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayInteractionDTO();
        interaction.setAction(com.bank.branch.platform.report.dto.req.presentation.ScreenInteractionAction.NONE);
        component.setInteraction(interaction);
        var ref = new com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayDataRefDTO();
        ref.setBlockId(12L);
        ref.setRole(com.bank.branch.platform.report.dto.req.presentation.ScreenDataRefRole.PRIMARY);
        ref.setUnit(ScreenDisplayUnit.YUAN);
        component.setDataRefs(List.of(ref));
        return component;
    }

    private ScreenDisplayComparisonDTO comparison(boolean enabled, Long historyBlockId, List<String> fields,
                                                  String dateField, ScreenDisplayUnit sourceUnit) {
        ScreenDisplayComparisonDTO comparison = new ScreenDisplayComparisonDTO();
        comparison.setEnabled(enabled);
        comparison.setHistoryBlockId(historyBlockId);
        comparison.setValueFields(fields);
        comparison.setDateField(dateField);
        comparison.setSourceUnit(sourceUnit);
        return comparison;
    }

    private String validStyle(String comparisonsJson) {
        return "{\"presentation\":{\"type\":\"CODE\",\"template\":\"branch-overview-v1\","
                + "\"displaySchemaVersion\":1,\"institutionRules\":{\"allowedOperatingLevels\":[\"PRIMARY\"],"
                + "\"allowedOrgNatures\":[\"SECONDARY_BRANCH\"]},\"display\":{\"components\":[{"
                + "\"componentId\":\"deposit-card\",\"componentType\":\"METRIC_CARD\","
                + "\"layoutRegion\":\"LEFT\",\"order\":0,\"visible\":true,"
                + "\"text\":{\"titleMode\":\"AUTO\"},\"format\":{\"displayUnit\":\"YUAN\"},"
                + "\"content\":{\"mainField\":\"value\"},\"interaction\":{\"action\":\"NONE\"},"
                + "\"dataRefs\":[{\"blockId\":12,\"role\":\"PRIMARY\",\"unit\":\"YUAN\"}]}],"
                + "\"comparisons\":" + comparisonsJson + "}}}}";
    }

    private String draft(long historyBlockId, String historyType) {
        return "{\"components\":[{\"component\":\"ChartWidget\",\"id\":\"primary\","
                + "\"blockId\":12,\"innerType\":\"METRIC_CARD\",\"propValue\":{\"bindingKey\":\"deposit\"},"
                + "\"bindJson\":\"{\\\"dsId\\\":12,\\\"period\\\":\\\"LATEST\\\","
                + "\\\"fields\\\":{\\\"value\\\":\\\"balance\\\"},\\\"units\\\":{\\\"value\\\":\\\"YUAN\\\"}}\"},"
                + "{\"component\":\"ChartWidget\",\"id\":\"history\",\"blockId\":" + historyBlockId
                + ",\"innerType\":\"" + historyType + "\",\"propValue\":{\"bindingKey\":\"trend\"},"
                + "\"bindJson\":\"{\\\"dsId\\\":13,\\\"period\\\":\\\"LAST_10D\\\","
                + "\\\"fields\\\":{\\\"date\\\":\\\"data_date\\\",\\\"deposit\\\":\\\"balance\\\"},"
                + "\\\"units\\\":{\\\"deposit\\\":\\\"YUAN\\\"}}\"}]}";
    }

    private RptScreenBlock block(long id, String componentType) {
        RptScreenBlock block = new RptScreenBlock();
        block.setId(id);
        block.setScreenId(8L);
        block.setComponentType(componentType);
        block.setBindJson(id == 12L
                ? "{\"dsId\":12,\"period\":\"LATEST\",\"fields\":{\"value\":\"balance\"},\"units\":{\"value\":\"YUAN\"}}"
                : "{\"dsId\":13,\"period\":\"LAST_10D\",\"fields\":{\"date\":\"data_date\",\"deposit\":\"balance\"},\"units\":{\"deposit\":\"YUAN\"}}");
        return block;
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateCanvasStyle(json))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }
}
