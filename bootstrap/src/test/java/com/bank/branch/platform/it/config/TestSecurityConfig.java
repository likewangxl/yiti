package com.bank.branch.platform.it.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 测试环境安全配置 - 提供最小化的认证检查。
 *
 * 在 test profile 下，Redis 不可用（MockRedisConnectionFactory 返回空结果），
 * 所以无法使用生产环境的 AuthenticationFilter（依赖 Redis 存储用户上下文）。
 *
 * 此配置提供一个简化版的认证过滤器：
 * 1. 检查 HttpSession 是否存在
 * 2. 不依赖 Redis/Session Store，直接检查 session 是否存在即为"已登录"
 * 3. 受保护路径无 session → 返回 401
 * 4. 登录/登出路径始终放行
 *
 * 注意: 此配置不执行 RBAC 权限校验（由各模块 Service 层处理），
 * 仅确保请求携带有效 Session。
 */
@Configuration
@Profile("test")
public class TestSecurityConfig {

    /**
     * 测试环境认证过滤器 - 仅检查 Session 是否存在。
     * 不依赖 Redis，检查逻辑与生产环境 AuthenticationFilter 一致。
     */
    @Bean
    public OncePerRequestFilter testAuthenticationFilter(ObjectMapper objectMapper) {
        return new OncePerRequestFilter() {
            private static final List<String> WHITE_LIST = List.of(
                    "/api/auth/login",
                    "/api/auth/logout",
                    "/doc.html",
                    "/webjars/",
                    "/swagger-resources/",
                    "/v3/api-docs/",
                    "/actuator/health",
                    "/error"
            );

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String path = request.getRequestURI();

                // 白名单路径放行
                for (String pattern : WHITE_LIST) {
                    if (path.startsWith(pattern)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                // 受保护路径必须携带有效 Session
                HttpSession session = request.getSession(false);
                if (session == null || session.getAttribute("empId") == null) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), Map.of(
                            "code", 40101,
                            "message", "未登录或登录已过期",
                            "traceId", ""
                    ));
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };
    }
}
