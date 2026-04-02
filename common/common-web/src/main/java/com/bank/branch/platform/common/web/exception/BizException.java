package com.bank.branch.platform.common.web.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 所有可预期的业务错误均抛出此异常或其子类
 */
@Getter
public class BizException extends RuntimeException {
    private final String code;
    private final String message;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BizException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
}
