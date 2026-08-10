package com.bank.branch.platform.auth.security.filter;

import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.common.web.exception.AuthException;
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
 * 每个受保护请求都会实时校验用户状态并按数据库中的有效角色重建角色派生上下文。
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
        "/api/auth/uniauth/login",
        "/api/auth/uniauth/redirect",
        "/api/auth/uniauth/callback",
        "/api/auth/logout",
        "/doc.html",
        "/webjars/**",
        "/swagger-resources/**",
        "/v3/api-docs/**",
        "/actuator/health",
        "/**/receiveCallPuRequest/**"
    );

    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final AuthService authService;

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

        CurrentUserContext refreshedCtx;
        try {
            // 实时重查用户状态：管理员禁用/锁定用户后，下一次请求立即失效（按主键查走索引 <1ms）
            PtUser freshUser = userMapper.selectByUserId(ctx.empId());
            if (freshUser == null
                    || Integer.valueOf(1).equals(freshUser.getIsEnabled())
                    || Integer.valueOf(1).equals(freshUser.getIsLocked())
                    || Integer.valueOf(1).equals(freshUser.getIsExpired())) {
                log.warn("[AuthFilter] 用户已被禁用/锁定/过期/删除，踢出 session empId={}", ctx.empId());
                session.invalidate();
                writeUnauthorized(response);
                return;
            }
            // 实时查询有效角色：旧 Session 自动升级，撤销/禁用角色与 SYS_ADMIN 回收立即生效
            refreshedCtx = authService.refreshRoleContext(ctx);
        } catch (AuthException ex) {
            if (isIdentityInvalid(ex)) {
                // 401 类认证异常代表用户/角色身份已经失效：销毁 Session，避免继续沿用旧权限。
                log.warn("[AuthFilter] 用户身份已失效，踢出 session empId={}, reason={}",
                        ctx.empId(), ex.getMessage());
                session.invalidate();
                writeUnauthorized(response);
                return;
            }
            // 其他 AuthException 不能被误判为身份失效；按基础设施故障 fail-close 并保留 Session。
            log.error("[AuthFilter] 认证服务异常，拒绝本次请求并保留 session empId={}, code={}",
                    ctx.empId(), ex.getCode(), ex);
            writeServiceUnavailable(response);
            return;
        } catch (RuntimeException ex) {
            // 数据库等基础设施异常只拒绝本次请求，不销毁仍可能有效的 Session，避免瞬时故障批量踢用户下线
            log.error("[AuthFilter] 认证基础设施暂不可用，拒绝本次请求并保留 session empId={}",
                    ctx.empId(), ex);
            writeServiceUnavailable(response);
            return;
        }
        if (!refreshedCtx.equals(ctx)) {
            session.setAttribute(AuthService.SESSION_USER_KEY, refreshedCtx);
        }

        // 将用户上下文设置到 ThreadLocal，供后续拦截器和业务层使用
        currentUserProvider.set(refreshedCtx);
        log.debug("[AuthFilter] 认证通过 empId={}", refreshedCtx.empId());
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

    /** 只有 401 类认证异常才表示当前用户或角色身份已经失效。 */
    private boolean isIdentityInvalid(AuthException ex) {
        return ex.getCode() != null && ex.getCode().startsWith("AUTH-401");
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

    /** 向客户端写入 HTTP 503 标准错误响应，不销毁现有 Session。 */
    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ResponseWrapper<?> body = ResponseWrapper.error(
                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode(),
                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
