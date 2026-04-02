package com.bank.branch.platform.common.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 将 traceId 写入响应头，便于客户端追踪
 */
public class TraceIdInterceptor implements HandlerInterceptor {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = MdcUtils.getTraceId();
        if (traceId != null) {
            response.setHeader(HEADER_TRACE_ID, traceId);
        }
        return true;
    }
}
