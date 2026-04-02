package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一捕获并转换各类异常为标准响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理认证异常 - 返回 401
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ResponseWrapper<?>> handleAuthException(AuthException ex) {
        log.warn("认证异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ResponseWrapper.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理权限拒绝异常 - 返回 403
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ResponseWrapper<?>> handlePermissionDeniedException(PermissionDeniedException ex) {
        log.warn("权限异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ResponseWrapper.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理业务异常 - 返回 200 + 业务错误码
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ResponseWrapper<?>> handleBizException(BizException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok(ResponseWrapper.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理 Spring Validation 异常 - 返回 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseWrapper<?>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ResponseWrapper.error("VALID_001", msg));
    }

    /**
     * 处理缺少请求参数异常 - 返回 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
            .body(ResponseWrapper.error("VALID_002", "缺少请求参数: " + ex.getParameterName()));
    }

    /**
     * 处理请求方法不支持异常 - 返回 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ResponseWrapper.error("SYS_405", "不支持的请求方法: " + ex.getMethod()));
    }

    /**
     * 处理媒体类型不支持异常 - 返回 415
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ResponseWrapper.error("SYS_415", "不支持的媒体类型"));
    }

    /**
     * 兜底异常处理 - 返回 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseWrapper<?>> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseWrapper.error("SYS_500", "系统繁忙，请稍后重试"));
    }
}
