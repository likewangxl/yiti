package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @Test
    void handlePermissionDenied_customCode_shouldPreserveCode() {
        PermissionDeniedException ex = new PermissionDeniedException("AUTH-40303", "写范围校验失败");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handlePermissionDeniedException(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("AUTH-40303", resp.getBody().getCode());
    }

    @Test
    void handleMissingParam_shouldReturn400() {
        MissingServletRequestParameterException ex =
            new MissingServletRequestParameterException("empId", "String");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleMissingParam(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("VALID_002", resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("empId"));
    }

    @Test
    void handleMediaTypeNotSupported_shouldReturn415() {
        HttpMediaTypeNotSupportedException ex =
            new HttpMediaTypeNotSupportedException("不支持的媒体类型");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleMediaTypeNotSupported(ex);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, resp.getStatusCode());
        assertEquals("SYS_415", resp.getBody().getCode());
    }

    @Test
    void handleAuthException_customCode_shouldPreserveCode() {
        AuthException ex = new AuthException("AUTH-40102", "账户已被锁定");
        ResponseEntity<ResponseWrapper<?>> resp = handler.handleAuthException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("AUTH-40102", resp.getBody().getCode());
    }
}
