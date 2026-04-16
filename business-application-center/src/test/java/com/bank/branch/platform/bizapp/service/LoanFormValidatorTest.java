package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LoanFormValidator 单元测试（TDD）。
 * 4个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class LoanFormValidatorTest {

    @InjectMocks
    private LoanFormValidator loanFormValidator;

    @Test
    void validate_loanCorpReview_allFieldsPresent_shouldPass() {
        // given
        Map<String, Object> formData = new HashMap<>();
        formData.put("creditAmount", "100000");
        formData.put("guaranteeType", "CREDIT");

        // when & then
        assertThatCode(() -> loanFormValidator.validate("loan_corp_review", formData))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_loanCorpReview_missingCreditAmount_shouldThrowBIZ42201() {
        // given
        Map<String, Object> formData = new HashMap<>();
        // creditAmount 缺失
        formData.put("guaranteeType", "CREDIT");

        // when & then
        assertThatThrownBy(() -> loanFormValidator.validate("loan_corp_review", formData))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42201");
    }

    @Test
    void validate_loanCorpReview_mortgageMissingCollateral_shouldThrowBIZ42202() {
        // given：担保方式为MORTGAGE但缺少抵押物描述
        Map<String, Object> formData = new HashMap<>();
        formData.put("creditAmount", "100000");
        formData.put("guaranteeType", "MORTGAGE");
        // collateralDesc 缺失

        // when & then
        assertThatThrownBy(() -> loanFormValidator.validate("loan_corp_review", formData))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42202");
    }

    @Test
    void validate_unknownNodeKey_shouldPassSilently() {
        // given
        Map<String, Object> formData = new HashMap<>();
        // 未知节点，校验应静默通过

        // when & then
        assertThatCode(() -> loanFormValidator.validate("unknown_node_key", formData))
                .doesNotThrowAnyException();
    }
}
