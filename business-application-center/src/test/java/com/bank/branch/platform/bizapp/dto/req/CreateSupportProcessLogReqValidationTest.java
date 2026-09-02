package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateSupportProcessLogReqValidationTest {

    private static Validator validator;
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("clientUuid 长度为64时通过 Bean Validation")
    void clientUuid_atMost64_isValid() {
        CreateSupportProcessLogReq request = validRequest();
        request.setClientUuid("x".repeat(64));

        Set<ConstraintViolation<CreateSupportProcessLogReq>> violations = validator.validate(request);

        assertThat(violations).noneMatch(violation ->
                violation.getPropertyPath().toString().equals("clientUuid"));
    }

    @Test
    @DisplayName("clientUuid 超过64时被 Bean Validation 拒绝")
    void clientUuid_over64_isRejected() {
        CreateSupportProcessLogReq request = validRequest();
        request.setClientUuid("x".repeat(65));

        Set<ConstraintViolation<CreateSupportProcessLogReq>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("clientUuid")
                        && violation.getMessage().equals("clientUuid长度不能超过64"));
    }

    private static CreateSupportProcessLogReq validRequest() {
        CreateSupportProcessLogReq request = new CreateSupportProcessLogReq();
        request.setClientUuid("client-uuid");
        request.setContent("办理过程");
        return request;
    }
}
