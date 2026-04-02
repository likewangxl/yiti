package com.bank.branch.platform.common.trace;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.io.IOException;
import java.util.UUID;

/**
 * traceId 生成 Filter
 * 在最高优先级运行，为每个请求生成唯一的 traceId 并注入 MDC
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter implements Filter {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String traceId = httpReq.getHeader(HEADER_TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            MdcUtils.putTraceId(traceId);
            chain.doFilter(request, response);
        } finally {
            MdcUtils.removeTraceId();
        }
    }
}
