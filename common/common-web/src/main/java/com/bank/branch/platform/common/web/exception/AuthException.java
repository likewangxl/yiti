package com.bank.branch.platform.common.web.exception;

/**
 * 认证异常，对应 HTTP 401
 */
public class AuthException extends BizException {
    private static final String DEFAULT_CODE = "AUTH_001";

    public AuthException(String message) {
        super(DEFAULT_CODE, message);
    }

    public AuthException(String code, String message) {
        super(code, message);
    }
}
