package com.bank.branch.platform.common.security.config;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.meta.ObjectMetaRegistry;
import jakarta.servlet.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import java.io.IOException;

/**
 * 安全模块自动配置类
 * 注册 ObjectMetaRegistry Bean 和 DataScope 清理过滤器
 * 通过 Spring Boot 自动配置机制在应用启动时自动加载
 */
@AutoConfiguration
public class SecurityAutoConfiguration {

    /**
     * 注册业务对象元数据注册中心
     *
     * @return ObjectMetaRegistry 实例
     */
    @Bean
    public ObjectMetaRegistry objectMetaRegistry() { return new ObjectMetaRegistry(); }

    /**
     * 注册 DataScope 清理过滤器
     * 确保每次请求结束后清理 ThreadLocal 中的数据范围上下文，防止内存泄漏
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<Filter> dataScopeCleanupFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                try { chain.doFilter(request, response); }
                finally { DataScopeContext.clear(); }
            }
        });
        reg.setOrder(Ordered.LOWEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
