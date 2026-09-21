package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** loanRate 独立单值比例槽位的纯内存契约测试。 */
class CodeScreenLoanRateContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STYLE = "{\"presentation\":{\"type\":\"CODE\","
            + "\"template\":\"branch-overview-v1\"}}";

    private String bind(String unit) {
        return "{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"value\":\"零售贷款目标完成率\"},"
                + "\"units\":{\"value\":\"" + unit + "\"}}";
    }

    private String draft(String bindingKey, String bindJson) throws Exception {
        return "{\"components\":[{\"component\":\"ChartWidget\","
                + "\"id\":\"w-loan-rate\",\"blockId\":12,"
                + "\"propValue\":{\"bindingKey\":\"" + bindingKey + "\"},"
                + "\"bindJson\":" + MAPPER.writeValueAsString(bindJson) + "}]}";
    }

    @Test
    void acceptsLoanRateAsIndependentOptionalBranchSlot() throws Exception {
        assertThat(CodeScreenPresentationValidator.slots()).contains("loanRate");
        CodeScreenPresentationValidator.validateDraft(STYLE, draft("loanRate", bind("PERCENT")));
        CodeScreenPresentationValidator.validateDraft(STYLE, draft("loanRate", bind("RATIO")));
    }

    @Test
    void loanRateRequiresRatioUnitAndRejectsAmountUnit() throws Exception {
        assertThatThrownBy(() -> CodeScreenPresentationValidator.validateDraft(
                STYLE, draft("loanRate", bind("YUAN"))))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
    }

    @Test
    void acceptsLoanRateMetadataWithoutChangingLegacyRate() {
        CodeScreenPresentationValidator.validateCanvasStyle("""
                {"presentation":{"type":"CODE","template":"branch-overview-v1"},
                 "metricLabels":{"rate":"存款目标完成率","loanRate":"零售贷款目标完成率"},
                 "sourceAvailability":{"loanRate":{"status":"NO_SOURCE",
                 "message":"当前来源未接入","fields":{"value":{"status":"NO_VALUES"}}}}}
                """);
    }

    @Test
    void legacyBranchPackageCanOmitOptionalLoanRate() throws Exception {
        String deposit = "{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"value\":\"存款余额\"},"
                + "\"units\":{\"value\":\"HUNDRED_MILLION\"}}";
        CodeScreenPresentationValidator.validateDraft(STYLE, draft("deposit", deposit));
    }
}
