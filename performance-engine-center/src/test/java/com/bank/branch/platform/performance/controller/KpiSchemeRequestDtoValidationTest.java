package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.AddKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.PublishKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiSchemeReqDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KPI 方案请求 DTO 的 JSR-303 校验 UT.
 */
class KpiSchemeRequestDtoValidationTest {

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

    @Test
    void createKpiSchemeReq_whenSchemeCodeLowercase_shouldViolation() {
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setSchemeCode("lower_case");
        req.setSchemeName("n");
        req.setCycleType("MONTHLY");
        req.setOpenDetail(Boolean.FALSE);

        Set<ConstraintViolation<CreateKpiSchemeReqDTO>> violations = validator.validateProperty(req, "schemeCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("schemeCode");
    }

    @Test
    void createKpiSchemeReq_whenCycleTypeInvalid_shouldViolation() {
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setCycleType("WEEKLY");

        Set<ConstraintViolation<CreateKpiSchemeReqDTO>> violations = validator.validateProperty(req, "cycleType");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("cycleType");
    }

    @Test
    void createKpiSchemeReq_whenOpenDetailNull_shouldViolation() {
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setOpenDetail(null);

        Set<ConstraintViolation<CreateKpiSchemeReqDTO>> violations = validator.validateProperty(req, "openDetail");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("openDetail");
    }

    @Test
    void updateKpiSchemeReq_whenAllNull_shouldNotViolate() {
        UpdateKpiSchemeReqDTO req = new UpdateKpiSchemeReqDTO();

        Set<ConstraintViolation<UpdateKpiSchemeReqDTO>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void updateKpiSchemeReq_whenCycleTypeInvalid_shouldViolation() {
        UpdateKpiSchemeReqDTO req = new UpdateKpiSchemeReqDTO();
        req.setCycleType("DAILY");

        Set<ConstraintViolation<UpdateKpiSchemeReqDTO>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("cycleType");
    }

    @Test
    void addKpiItemReq_whenWeightNegative_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("METRIC_OK");
        req.setWeight(new BigDecimal("-1"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "weight");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("weight");
    }

    @Test
    void addKpiItemReq_whenWeightOverHundred_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("METRIC_OK");
        req.setWeight(new BigDecimal("200"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "weight");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("weight");
    }

    @Test
    void addKpiItemReq_whenMetricCodeBlank_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode(" ");
        req.setWeight(new BigDecimal("10"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "metricCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("metricCode");
    }

    @Test
    void addKpiItemReq_whenMetricCodeLowercase_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("lower");
        req.setWeight(new BigDecimal("10"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "metricCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("metricCode");
    }

    @Test
    void addKpiItemReq_whenMetricCodeStartsWithDigit_shouldViolation() {
        // Pattern 收严至 ^[A-Z][A-Z0-9_]*$ 后, 首字符必须是大写字母, 数字/下划线开头非法
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("1METRIC");
        req.setWeight(new BigDecimal("10"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "metricCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("metricCode");
    }

    @Test
    void addKpiItemReq_whenMetricCodeStartsWithUnderscore_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("_METRIC");
        req.setWeight(new BigDecimal("10"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "metricCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("metricCode");
    }

    @Test
    void updateKpiItemReq_whenAllNull_shouldNotViolate() {
        UpdateKpiItemReqDTO req = new UpdateKpiItemReqDTO();

        Set<ConstraintViolation<UpdateKpiItemReqDTO>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void publishKpiSchemeReq_whenReasonBlank_shouldViolation() {
        PublishKpiSchemeReqDTO req = new PublishKpiSchemeReqDTO();
        req.setReason(" ");

        Set<ConstraintViolation<PublishKpiSchemeReqDTO>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("reason");
    }
}
