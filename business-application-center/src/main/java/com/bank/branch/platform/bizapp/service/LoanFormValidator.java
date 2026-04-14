package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 贷款节点表单校验服务。
 * <p>
 * V1 仅硬编码支持 {@code loan_corp_review} 节点的校验规则。
 * 未知节点静默通过，便于未来按节点扩展。
 * </p>
 */
@Slf4j
@Service
public class LoanFormValidator {

    /** 抵押方式常量 */
    private static final String GUARANTEE_MORTGAGE = "MORTGAGE";

    /**
     * 校验节点表单数据。
     * <p>
     * 当前支持节点：{@code loan_corp_review}
     * <ul>
     *   <li>必填字段：{@code creditAmount}、{@code guaranteeType}</li>
     *   <li>条件必填：若 {@code guaranteeType == "MORTGAGE"}，则 {@code collateralDesc} 也必填</li>
     * </ul>
     * 未知节点静默通过。
     * </p>
     *
     * @param nodeKey  节点标识（如 "loan_corp_review"）
     * @param formData 表单数据 Map
     * @throws BizException BIZ-42201 必填字段缺失；BIZ-42202 条件校验失败
     */
    public void validate(String nodeKey, Map<String, Object> formData) {
        log.debug("[LoanFormValidator.validate] nodeKey={}", nodeKey);

        if ("loan_corp_review".equals(nodeKey)) {
            validateLoanCorpReview(formData);
        }
        // 其他节点暂不校验，静默通过
    }

    /**
     * 校验 loan_corp_review 节点表单。
     *
     * @param formData 表单数据
     */
    private void validateLoanCorpReview(Map<String, Object> formData) {
        // 1. 必填字段：creditAmount
        if (!hasValue(formData, "creditAmount")) {
            throw new BizException(
                    BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getCode(),
                    BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getMessage() + ": creditAmount"
            );
        }

        // 2. 必填字段：guaranteeType
        if (!hasValue(formData, "guaranteeType")) {
            throw new BizException(
                    BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getCode(),
                    BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getMessage() + ": guaranteeType"
            );
        }

        // 3. 条件必填：若 guaranteeType == "MORTGAGE"，collateralDesc 必填
        Object guaranteeType = formData.get("guaranteeType");
        if (GUARANTEE_MORTGAGE.equals(guaranteeType) && !hasValue(formData, "collateralDesc")) {
            throw new BizException(
                    BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getCode(),
                    BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getMessage() + ": collateralDesc required when guaranteeType=MORTGAGE"
            );
        }
    }

    /**
     * 判断表单字段是否存在且非空字符串。
     *
     * @param formData  表单数据
     * @param fieldName 字段名
     * @return true 表示字段存在且有值
     */
    private boolean hasValue(Map<String, Object> formData, String fieldName) {
        Object value = formData.get(fieldName);
        if (value == null) {
            return false;
        }
        if (value instanceof String && ((String) value).isBlank()) {
            return false;
        }
        return true;
    }
}
