package com.bank.branch.platform.common.web.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BizExceptionTest {

    @Test
    void shouldCarryCodeAndMessage() {
        BizException ex = new BizException("USER_001", "用户不存在");
        assertEquals("USER_001", ex.getCode());
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void shouldCarryCodeMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BizException ex = new BizException("DB_001", "数据库异常", cause);
        assertEquals("DB_001", ex.getCode());
        assertEquals("数据库异常", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void authExceptionShouldUseDefaultCode() {
        AuthException ex = new AuthException("登录态失效");
        assertEquals("AUTH_001", ex.getCode());
        assertEquals("登录态失效", ex.getMessage());
        assertInstanceOf(BizException.class, ex);
    }

    @Test
    void authExceptionShouldAcceptCustomCode() {
        AuthException ex = new AuthException("AUTH-40106", "账户已锁定");
        assertEquals("AUTH-40106", ex.getCode());
    }

    @Test
    void permissionDeniedShouldUseDefaultCode() {
        PermissionDeniedException ex = new PermissionDeniedException("无权访问");
        assertEquals("PERM_001", ex.getCode());
        assertInstanceOf(BizException.class, ex);
    }

    @Test
    void permissionDeniedShouldAcceptCustomCode() {
        PermissionDeniedException ex = new PermissionDeniedException("SCOPE_001", "数据范围不足");
        assertEquals("SCOPE_001", ex.getCode());
    }
}
