package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.ChangeStatusReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateMetricReqDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标请求 DTO 参数校验测试。
 */
class MetricRequestDtoValidationTest {

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
    void createMetricReq_whenMetricCodeLowercase_shouldViolation() {
        CreateMetricReqDTO req = new CreateMetricReqDTO();
        req.setMetricCode("metric_code");

        Set<ConstraintViolation<CreateMetricReqDTO>> violations = validator.validateProperty(req, "metricCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("metricCode");
    }

    @Test
    void changeStatusReq_whenReasonBlank_shouldViolation() {
        ChangeStatusReqDTO req = new ChangeStatusReqDTO();
        req.setReason(" ");

        Set<ConstraintViolation<ChangeStatusReqDTO>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("reason");
    }

    @Test
    void releaseSlotReq_whenReasonBlank_shouldViolation() {
        ReleaseSlotReqDTO req = new ReleaseSlotReqDTO();
        req.setReason(" ");

        Set<ConstraintViolation<ReleaseSlotReqDTO>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("reason");
    }
}
