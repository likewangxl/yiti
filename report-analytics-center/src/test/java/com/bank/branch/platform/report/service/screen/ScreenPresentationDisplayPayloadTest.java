package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.dto.req.CodeScreenPresentationDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenComponentType;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDataRefRole;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayComponentDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayContentDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayDataRefDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayFormatDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayInteractionDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayPayloadDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayTextDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayUnit;
import com.bank.branch.platform.report.dto.req.presentation.ScreenInteractionAction;
import com.bank.branch.platform.report.dto.req.presentation.ScreenLayoutRegion;
import com.bank.branch.platform.report.dto.req.presentation.ScreenSourceDimension;
import com.bank.branch.platform.report.dto.req.presentation.ScreenTitleMode;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreenPresentationDisplayPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void codePresentationMapsDisplayVersionBesideDisplayAndRoundTripsTypedPayload() throws Exception {
        CanvasStyleDTO style = new CanvasStyleDTO();
        CodeScreenPresentationDTO presentation = new CodeScreenPresentationDTO();
        presentation.setType("CODE");
        presentation.setTemplate("branch-overview-v1");
        presentation.setDisplaySchemaVersion(1);
        ScreenDisplayPayloadDTO payload = new ScreenDisplayPayloadDTO();
        payload.setComponents(java.util.List.of(component(12L)));
        presentation.setDisplay(payload);
        style.setPresentation(presentation);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(style));

        assertThat(json.path("presentation").path("displaySchemaVersion").intValue()).isEqualTo(1);
        assertThat(json.path("presentation").path("display").path("components")).isNotEmpty();
        assertThat(json.path("presentation").path("display").has("displaySchemaVersion")).isFalse();

        CanvasStyleDTO roundTrip = MAPPER.treeToValue(json, CanvasStyleDTO.class);
        assertThat(roundTrip.getPresentation().getDisplaySchemaVersion()).isEqualTo(1);
        assertThat(roundTrip.getPresentation().getDisplay().getComponents()).hasSize(1);
        assertThat(roundTrip.getPresentation().getDisplay().getComponents().get(0).getDataRefs().get(0).getBlockId())
                .isEqualTo(12L);
    }

    @Test
    void legacyPresentationWithoutDisplayVersionKeepsOriginalPath() {
        var style = MAPPER.createObjectNode();
        style.putObject("presentation").put("type", "CODE").put("template", "branch-overview-v1");
        assertThat(CodeScreenPresentationValidator.presentationTemplate(style))
                .isEqualTo("branch-overview-v1");
    }

    @Test
    void displayVersionOneIsStrictlyValidated() throws Exception {
        CanvasStyleDTO style = new CanvasStyleDTO();
        CodeScreenPresentationDTO presentation = new CodeScreenPresentationDTO();
        presentation.setType("CODE");
        presentation.setTemplate("branch-overview-v1");
        presentation.setDisplaySchemaVersion(1);
        ScreenDisplayPayloadDTO payload = new ScreenDisplayPayloadDTO();
        payload.setComponents(java.util.List.of(component(12L)));
        presentation.setDisplay(payload);
        style.setPresentation(presentation);

        CodeScreenPresentationValidator.validateCanvasStyle(MAPPER.writeValueAsString(style));
    }

    @Test
    void unknownDisplayVersionIsRejected() {
        assertInvalid("{\"presentation\":{\"type\":\"CODE\",\"template\":\"branch-overview-v1\","
                + "\"displaySchemaVersion\":9,\"display\":{\"components\":[]}}}");
    }

    @Test
    void unknownDisplayFieldsAreRejectedRecursively() {
        String json = validJson().replace("\"components\":[", "\"unknown\":true,\"components\":[");
        assertInvalid(json);
    }

    @Test
    void displayVersionCannotBeNestedInsideDisplay() {
        String json = validJson().replace("\"display\":{\"components\":",
                "\"display\":{\"displaySchemaVersion\":1,\"components\":");
        assertInvalid(json);
    }

    @Test
    void unknownEnumIsRejected() {
        String json = validJson().replace("\"componentType\":\"METRIC_CARD\"",
                "\"componentType\":\"UNKNOWN\"");
        assertInvalid(json);
    }

    @Test
    void draftAcceptsCurrentBlockReferenceAndRejectsReferenceOutsideComponentTree() {
        String draft = draftJson(12L);
        CodeScreenPresentationValidator.validateDraft(validJson(), draft);

        String otherBlockStyle = validJson().replace("\"blockId\":12", "\"blockId\":99");
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(otherBlockStyle, draft))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void publishedPackageKeepsDisplayContractAndRejectsUntrustedReference() throws Exception {
        String draft = draftJson(12L);
        JsonNode component = MAPPER.readTree(draft).path("components").get(0);
        var root = MAPPER.createObjectNode();
        root.put("schemaVersion", 2);
        root.set("canvasStyle", MAPPER.readTree(validJson()));
        root.putArray("components").add(component);
        root.putObject("bindSnapshots").putObject("12")
                .put("componentType", "METRIC_CARD")
                .set("bind", MAPPER.readTree(bindJson()));
        CodeScreenPresentationValidator.validatePublishedPackage(root);

        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("canvasStyle")
                .path("presentation").path("display").path("components").get(0)
                .path("dataRefs").get(0)).put("blockId", 99);
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validatePublishedPackage(root))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateCanvasStyle(json))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    private String validJson() {
        try {
            CanvasStyleDTO style = new CanvasStyleDTO();
            CodeScreenPresentationDTO presentation = new CodeScreenPresentationDTO();
            presentation.setType("CODE");
            presentation.setTemplate("branch-overview-v1");
            presentation.setDisplaySchemaVersion(1);
            ScreenDisplayPayloadDTO payload = new ScreenDisplayPayloadDTO();
            payload.setComponents(java.util.List.of(component(12L)));
            presentation.setDisplay(payload);
            style.setPresentation(presentation);
            return MAPPER.writeValueAsString(style);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private ScreenDisplayComponentDTO component(long blockId) {
        ScreenDisplayComponentDTO component = new ScreenDisplayComponentDTO();
        component.setComponentId("deposit-card");
        component.setComponentType(ScreenComponentType.METRIC_CARD);
        component.setLayoutRegion(ScreenLayoutRegion.LEFT);
        component.setOrder(0);
        component.setVisible(true);

        ScreenDisplayTextDTO text = new ScreenDisplayTextDTO();
        text.setTitleMode(ScreenTitleMode.CUSTOM);
        text.setTitle("存款");
        text.setSubtitle("");
        text.setDescription("");
        component.setText(text);

        ScreenDisplayFormatDTO format = new ScreenDisplayFormatDTO();
        format.setDisplayUnit(ScreenDisplayUnit.YUAN);
        format.setDecimals(0);
        format.setThousandsSeparator(false);
        component.setFormat(format);

        ScreenDisplayContentDTO content = new ScreenDisplayContentDTO();
        content.setMainField("value");
        component.setContent(content);

        ScreenDisplayInteractionDTO interaction = new ScreenDisplayInteractionDTO();
        interaction.setAction(ScreenInteractionAction.NONE);
        component.setInteraction(interaction);

        ScreenDisplayDataRefDTO ref = new ScreenDisplayDataRefDTO();
        ref.setBlockId(blockId);
        ref.setRole(ScreenDataRefRole.PRIMARY);
        ref.setUnit(ScreenDisplayUnit.YUAN);
        ref.setDimension(ScreenSourceDimension.ORG);
        component.setDataRefs(java.util.List.of(ref));
        return component;
    }

    private String draftJson(long blockId) {
        return "{\"schemaVersion\":2,\"components\":[{\"component\":\"ChartWidget\","
                + "\"id\":\"w-deposit\",\"blockId\":" + blockId + ","
                + "\"propValue\":{\"bindingKey\":\"deposit\"},"
                + "\"bindJson\":" + quote(bindJson()) + "}]}";
    }

    private String bindJson() {
        return "{\"dsId\":12,\"period\":\"LATEST\",\"fields\":{\"value\":\"balance\"},"
                + "\"units\":{\"value\":\"YUAN\"}}";
    }

    private String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
