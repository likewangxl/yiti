package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.CreateTargetPlanReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateTargetPlanReqDTO;
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
import java.time.LocalDate;
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

    // =================== CreateTargetPlanReqDTO ===================

    @Test
    void createPlanReq_whenPlanCodeLowercase_shouldViolation() {
        CreateTargetPlanReqDTO req = okPlanReq();
        req.setPlanCode("bad_lower");

        Set<ConstraintViolation<CreateTargetPlanReqDTO>> violations = validator.validateProperty(req, "planCode");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("planCode");
    }

    @Test
    void createPlanReq_whenTargetDimInvalid_shouldViolation() {
        CreateTargetPlanReqDTO req = okPlanReq();
        req.setTargetDim("CUST"); // DDL 仅允许 EMP/ORG

        Set<ConstraintViolation<CreateTargetPlanReqDTO>> violations = validator.validateProperty(req, "targetDim");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("targetDim");
    }

    @Test
    void createPlanReq_whenTargetCycleInvalid_shouldViolation() {
        CreateTargetPlanReqDTO req = okPlanReq();
        req.setTargetCycle("MONTH"); // DDL 仅允许 YEAR/QUARTER

        Set<ConstraintViolation<CreateTargetPlanReqDTO>> violations = validator.validateProperty(req, "targetCycle");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("targetCycle");
    }

    @Test
    void createPlanReq_whenEffectiveDateNull_shouldViolation() {
        CreateTargetPlanReqDTO req = okPlanReq();
        req.setEffectiveDate(null);

        Set<ConstraintViolation<CreateTargetPlanReqDTO>> violations = validator.validateProperty(req, "effectiveDate");
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("effectiveDate");
    }

    @Test
    void createPlanReq_whenAllValid_shouldPass() {
        CreateTargetPlanReqDTO req = okPlanReq();

        Set<ConstraintViolation<CreateTargetPlanReqDTO>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    // =================== UpdateTargetPlanReqDTO ===================

    @Test
    void updatePlanReq_whenAllNull_shouldNotViolate() {
        // plan L1406 钦定: update 不强制 reason, 全 null 合法
        UpdateTargetPlanReqDTO req = new UpdateTargetPlanReqDTO();

        Set<ConstraintViolation<UpdateTargetPlanReqDTO>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void updatePlanReq_whenTargetCycleInvalid_shouldViolation() {
        UpdateTargetPlanReqDTO req = new UpdateTargetPlanReqDTO();
        req.setTargetCycle("DAILY");

        Set<ConstraintViolation<UpdateTargetPlanReqDTO>> violations = validator.validate(req);
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString).contains("targetCycle");
    }

    // =================== helpers ===================

    private static CreateTargetPlanReqDTO okPlanReq() {
        CreateTargetPlanReqDTO req = new CreateTargetPlanReqDTO();
        req.setPlanCode("TEST_PLAN_OK");
        req.setPlanName("测试方案");
        req.setKpiSchemeId("kpi-id-32-char");
        req.setTargetDim("EMP");
        req.setTargetCycle("YEAR");
        req.setEffectiveDate(LocalDate.now());
        return req;
    }

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
