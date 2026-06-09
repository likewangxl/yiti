package com.bank.branch.platform.soap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * callpu 入口 Content-Type 归一化过滤器（SYS_415 修复）。
 *
 * <p><b>根因</b>：手机端 / ICPS 旧网关用 {@code application/x-www-form-urlencoded} 提交，
 * 但请求体本身是 JSON 文本；而 {@code CallPuController#dispatch} 用 {@code @RequestBody} 反序列化为
 * {@code CallPuRequest}，Jackson 只接受 {@code application/json} → Spring 抛
 * {@code HttpMediaTypeNotSupportedException} → SYS_415。</p>
 *
 * <p><b>为什么不能只让 Jackson 接受 urlencoded</b>：当 Content-Type 是 urlencoded 时，
 * Servlet 容器会把请求体当成表单参数解析，Spring 在 {@code @RequestBody} 取 body 时会
 * 按表单参数“反向 URL 编码”重建请求体（{@code ServletServerHttpRequest#getBodyFromServletRequestParameters}），
 * 导致 Jackson 收到的是被 URL 编码后的 {@code %7B...} 而非原始 JSON，解析失败（400）。</p>
 *
 * <p><b>方案</b>：在进入 Spring MVC 之前，对 {@code /api/callpu} 且 Content-Type 为
 * urlencoded 的请求，包一层 wrapper 把 Content-Type “改写”为 {@code application/json}。
 * 这样容器不会把它当表单解析、不消费 body，Jackson 直接读到原始 JSON 体。
 * 过滤器只改写头、不读取 body，确保 {@code @RequestBody} 仍拿到完整请求体。</p>
 *
 * <p>过滤器优先级设为最高，确保早于任何会触发 {@code getParameter} 的逻辑执行。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CallPuContentTypeNormalizationFilter extends OncePerRequestFilter {

    /** callpu 统一入口路径（对齐 {@code CallPuController} 的 {@code @RequestMapping("/api/callpu")}）。 */
    private static final String CALLPU_PATH = "/api/callpu";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean isCallPu = request.getRequestURI() != null && request.getRequestURI().endsWith(CALLPU_PATH);
        boolean isFormUrlencoded = contentType != null
                && contentType.toLowerCase().startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        if (isCallPu && isFormUrlencoded) {
            filterChain.doFilter(new JsonContentTypeRequestWrapper(request), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /** 把 Content-Type 对外伪装成 application/json 的请求包装器（仅改写头，不动 body）。 */
    private static final class JsonContentTypeRequestWrapper extends HttpServletRequestWrapper {

        JsonContentTypeRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON_VALUE;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                return MediaType.APPLICATION_JSON_VALUE;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(MediaType.APPLICATION_JSON_VALUE));
            }
            return super.getHeaders(name);
        }
    }
}
