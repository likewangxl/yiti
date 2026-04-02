package com.bank.branch.platform.common.web.exception;

/**
 * 权限拒绝异常，对应 HTTP 403
 */
public class PermissionDeniedException extends BizException {
    private static final String DEFAULT_CODE = "PERM_001";

    public PermissionDeniedException(String message) {
        super(DEFAULT_CODE, message);
    }

    public PermissionDeniedException(String code, String message) {
        super(code, message);
    }
}
