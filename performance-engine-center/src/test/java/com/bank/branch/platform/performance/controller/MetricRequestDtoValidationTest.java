package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.ChangeStatusReqDTO;
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

    // V1.6 已显式去除 metricCode 的 @Pattern 约束（"放开格式限制：业务侧反馈大写+数字+下划线
    // 约束太死"，见 CreateMetricReqDTO#metricCode 注释），lowercase 现为合法值。
    // 原 createMetricReq_whenMetricCodeLowercase_shouldViolation 反契约测试 V1.10 删除。

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
