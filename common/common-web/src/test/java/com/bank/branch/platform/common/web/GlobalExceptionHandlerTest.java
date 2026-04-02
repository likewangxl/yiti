package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBizExceptionShouldReturn200WithErrorCode() {
        BizException ex = new BizException("USER_001", "用户不存在");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleBizException(ex);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("USER_001", resp.getBody().getCode());
    }

    @Test
    void handleAuthExceptionShouldReturn401() {
        AuthException ex = new AuthException("登录态失效");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleAuthException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("AUTH_001", resp.getBody().getCode());
    }

    @Test
    void handlePermissionDeniedShouldReturn403() {
        PermissionDeniedException ex = new PermissionDeniedException("无权访问");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handlePermissionDeniedException(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("PERM_001", resp.getBody().getCode());
    }

    @Test
    void handleMethodNotSupportedShouldReturn405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleMethodNotSupported(ex);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
    }

    @Test
    void handleUnknownExceptionShouldReturn500() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("SYS_500", resp.getBody().getCode());
    }
}
