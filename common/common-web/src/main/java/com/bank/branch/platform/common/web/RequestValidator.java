package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请求参数手动校验工具
 * 用于非 Controller 层的参数校验场景
 */
public class RequestValidator {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private RequestValidator() {}

    /**
     * 校验对象的 JSR-303 注解约束
     *
     * @param object 待校验对象
     * @throws BizException 校验失败时抛出业务异常
     */
    public static <T> void validate(T object) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
            throw new BizException("VALID_001", message);
        }
    }
}
