package com.bank.branch.platform.auth.security.filter;

import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 认证过滤器
 * 责任：从 HttpSession 中恢复 CurrentUserContext 并放入 ThreadLocal。
 * 白名单 URL 直接放行，无需认证。
 * 未登录或 Session 失效时返回 HTTP 401 + 标准错误响应体。
 * 无论请求结果如何，finally 块都会清理 ThreadLocal 防止内存泄漏。
 */
@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    /** 不需要认证即可访问的 URL 白名单（支持 Ant 通配符） */
    static final List<String> WHITELIST = List.of(
        "/api/auth/login",
        "/api/auth/logout",
        "/doc.html",
        "/webjars/**",
        "/swagger-resources/**",
        "/v3/api-docs/**",
        "/actuator/health"
    );

    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 白名单 URL 直接放行，不校验 Session
        if (isWhitelisted(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 尝试从 Session 恢复用户上下文（false = 不自动创建新 Session）
        HttpSession session = request.getSession(false);
        Object attr = session != null ? session.getAttribute(AuthService.SESSION_USER_KEY) : null;

        if (!(attr instanceof CurrentUserContext ctx)) {
            // 未登录或 Session 已过期：Fail Close，返回 401
            writeUnauthorized(response);
            return;
        }

        // 将用户上下文设置到 ThreadLocal，供后续拦截器和业务层使用
        currentUserProvider.set(ctx);
        log.debug("[AuthFilter] 认证通过 empId={}", ctx.empId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无论是否异常都清理 ThreadLocal，防止线程复用时数据污染
            currentUserProvider.clear();
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /** 判断请求 URI 是否在白名单中（支持 Ant 通配符） */
    private boolean isWhitelisted(String uri) {
        return WHITELIST.stream().anyMatch(pattern -> antPathMatcher.match(pattern, uri));
    }

    /** 向客户端写入 HTTP 401 标准错误响应 */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ResponseWrapper<?> body = ResponseWrapper.error(
            AuthErrorCode.NOT_AUTHENTICATED.getCode(),
            AuthErrorCode.NOT_AUTHENTICATED.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
