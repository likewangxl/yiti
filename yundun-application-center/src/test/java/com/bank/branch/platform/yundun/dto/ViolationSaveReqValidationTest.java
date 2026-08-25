package com.bank.branch.platform.yundun.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 违规信息新增/编辑请求的字段校验契约。 */
class ViolationSaveReqValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void accountabilityWorkNumberMustNotBeBlank() {
        AccountabilityViolationSaveReq request = new AccountabilityViolationSaveReq();
        request.setWorkNumber("  ");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("workNumber");
                    assertThat(violation.getMessage()).isEqualTo("工号不能为空");
                });
    }

    @Test
    void creditEmployeeNumberMustNotBeBlank() {
        CreditViolationSaveReq request = new CreditViolationSaveReq();
        request.setEmployeeNumber("  ");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("employeeNumber");
                    assertThat(violation.getMessage()).isEqualTo("员工工号不能为空");
                });
    }
}
