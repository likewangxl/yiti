package com.bank.branch.platform.auth.config;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.security.filter.AuthenticationFilter;
import com.bank.branch.platform.auth.security.interceptor.AuthorizationInterceptor;
import com.bank.branch.platform.auth.security.matcher.ResourceMatcher;
import com.bank.branch.platform.auth.security.resolver.BizMetaResolver;
import com.bank.branch.platform.auth.security.resolver.RbacAuthorizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 安全配置
 * 注册认证过滤器（AuthenticationFilter）和授权拦截器（AuthorizationInterceptor）。
 * 过滤器最先执行（Order=1），拦截器在 DispatcherServlet 内执行。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final CurrentUserProvider currentUserProvider;
    private final ResourceMatcher resourceMatcher;
    private final RbacAuthorizer rbacAuthorizer;
    private final BizMetaResolver bizMetaResolver;
    private final BizScopeApi bizScopeApi;
    private final ObjectMapper objectMapper;

    /**
     * 将 AuthenticationFilter 注册为 Servlet Filter
     * 优先级最高（Order=1），对所有 URL 路径生效（/*）
     */
    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilterBean() {
        AuthenticationFilter filter = new AuthenticationFilter(currentUserProvider, objectMapper);
        FilterRegistrationBean<AuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(1);
        bean.setName("authenticationFilter");
        return bean;
    }

    /**
     * 注册 AuthorizationInterceptor 到 Spring MVC 拦截器链
     * 拦截所有 /api/** 路径（白名单由 AuthenticationFilter 已处理，此处不再排除）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizationInterceptor(
                resourceMatcher, rbacAuthorizer, bizMetaResolver,
                bizScopeApi, currentUserProvider, objectMapper))
            .addPathPatterns("/api/**")
            // 排除不需要授权校验的路径（认证由过滤器处理，白名单路径无需 RBAC）
            .excludePathPatterns(
                "/api/auth/login",
                "/api/auth/logout",
                "/doc.html",
                "/webjars/**",
                "/swagger-resources/**",
                "/v3/api-docs/**",
                "/actuator/health"
            );
    }
}
