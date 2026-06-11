package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
     * 处理非法参数异常 - 返回 400
     * 包括控制器内手动抛出的 IllegalArgumentException（如参数校验）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseWrapper<?>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("非法参数: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(ResponseWrapper.error("VALID_003", ex.getMessage()));
    }

    /**
     * 处理路径变量/查询参数类型不匹配异常 - 返回 400
     * 例如 PathVariable @DateTimeFormat 解析失败等
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseWrapper<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = "参数类型错误: " + ex.getName() + "=" + ex.getValue();
        log.warn("参数类型不匹配: {}", msg);
        return ResponseEntity.badRequest().body(ResponseWrapper.error("VALID_004", msg));
    }

    /**
     * 处理请求体 JSON 解析异常 - 返回 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseWrapper<?>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
            .body(ResponseWrapper.error("VALID_005", "请求体格式错误: " + ex.getMostSpecificCause().getMessage()));
    }

    /**
     * 兜底异常处理 - 返回 500
     * <p>客户端提前断连（浏览器刷新/关页、网关回收空闲连接、服务重启瞬间）会在写响应 flush 时抛
     * ClientAbortException / IOException(Broken pipe…)，这不是服务端故障，降级为 debug 不打 ERROR 堆栈，
     * 避免日志刷屏误导排查。返回体此时也写不回客户端，仅占位。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseWrapper<?>> handleException(Exception ex) {
        if (isClientAbort(ex)) {
            log.debug("客户端提前断开连接（忽略）: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseWrapper.error("SYS_499", "客户端连接已中断"));
        }
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseWrapper.error("SYS_500", "系统繁忙，请稍后重试"));
    }

    /**
     * 判断异常链是否为「客户端断连」。
     * <p>不硬依赖某容器的 ClientAbortException 类（BES 用 com.bes.* / Tomcat 用 org.apache.catalina.*），
     * 改用「类名 == ClientAbortException」或「IOException 且 message 命中断连关键词」跨容器识别。</p>
     */
    private boolean isClientAbort(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if ("ClientAbortException".equals(t.getClass().getSimpleName())) {
                return true;
            }
            String m = t.getMessage();
            if (t instanceof java.io.IOException && m != null) {
                String s = m.toLowerCase();
                if (s.contains("broken pipe") || s.contains("connection reset")
                        || s.contains("aborted") || s.contains("中止")) {
                    return true;
                }
            }
            if (t.getCause() == t) {
                break;   // 防御自引用导致死循环
            }
        }
        return false;
    }
}
