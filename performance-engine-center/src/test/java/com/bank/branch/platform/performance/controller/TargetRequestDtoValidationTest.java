package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueBatchReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueReqDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TargetPlan / TargetValue 请求 DTO 的 JSR-303 校验 UT.
 *
 * <p>覆盖 UpsertTargetValueReqDTO / UpsertTargetValueBatchReqDTO 的基础校验规则.
 * TargetPlan DTO 的校验将在 Step 3 红阶段补充.
 */
class TargetRequestDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    // =================== UpsertTargetValueReqDTO ===================

    @Test
    void upsertTargetValueReq_whenPlanIdBlank_shouldViolation() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setPlanId(" ");

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "planId");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("planId");
    }

    @Test
    void upsertTargetValueReq_whenSubjectTypeInvalid_shouldViolation() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setSubjectType("CUST"); // 只允许 EMP/ORG

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "subjectType");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("subjectType");
    }

    @Test
    void upsertTargetValueReq_whenCycleKeyMalformed_shouldViolation() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setCycleKey("2026X");

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "cycleKey");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("cycleKey");
    }

    @Test
    void upsertTargetValueReq_whenCycleKeyYearOnly_shouldPass() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setCycleKey("2026");

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "cycleKey");
        assertThat(violations).isEmpty();
    }

    @Test
    void upsertTargetValueReq_whenCycleKeyQuarter_shouldPass() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setCycleKey("2026Q1");

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "cycleKey");
        assertThat(violations).isEmpty();
    }

    @Test
    void upsertTargetValueReq_whenMetricCodeLowercase_shouldViolation() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setMetricCode("lower");

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "metricCode");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("metricCode");
    }

    @Test
    void upsertTargetValueReq_whenTargetValueNull_shouldViolation() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setTargetValue(null);

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "targetValue");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("targetValue");
    }

    @Test
    void upsertTargetValueReq_whenBaseValueNull_shouldPass() {
        UpsertTargetValueReqDTO req = okValueReq();
        req.setBaseValue(null);

        Set<ConstraintViolation<UpsertTargetValueReqDTO>> violations = validator.validateProperty(req, "baseValue");
        assertThat(violations).isEmpty();
    }

    // =================== UpsertTargetValueBatchReqDTO ===================

    @Test
    void batchReq_whenValuesEmpty_shouldViolation() {
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        req.setValues(List.of());

        Set<ConstraintViolation<UpsertTargetValueBatchReqDTO>> violations = validator.validate(req);
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("values");
    }

    @Test
    void batchReq_whenValuesNull_shouldViolation() {
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        req.setValues(null);

        Set<ConstraintViolation<UpsertTargetValueBatchReqDTO>> violations = validator.validate(req);
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("values");
    }

    @Test
    void batchReq_when501Items_shouldViolation() {
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        List<UpsertTargetValueReqDTO> list = new ArrayList<>(501);
        for (int i = 0; i < 501; i++) {
            list.add(okValueReq());
        }
        req.setValues(list);

        Set<ConstraintViolation<UpsertTargetValueBatchReqDTO>> violations = validator.validate(req);
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("values");
    }

    @Test
    void batchReq_when500Items_shouldPass() {
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        List<UpsertTargetValueReqDTO> list = new ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            list.add(okValueReq());
        }
        req.setValues(list);

        Set<ConstraintViolation<UpsertTargetValueBatchReqDTO>> violations = validator.validate(req);
        // 500 不违反 @Size(max=500), 但内部元素仍需通过; 这里 okValueReq 返回合法 DTO
        assertThat(violations).isEmpty();
    }

    // =================== helpers ===================

    private static UpsertTargetValueReqDTO okValueReq() {
        UpsertTargetValueReqDTO req = new UpsertTargetValueReqDTO();
        req.setPlanId("PLAN_123");
        req.setSubjectType("EMP");
        req.setSubjectId("E001");
        req.setCycleKey("2026");
        req.setMetricCode("METRIC_OK");
        req.setTargetValue(new BigDecimal("100.0000"));
        return req;
    }
}
