package com.bank.branch.platform.common.trace.config;

import com.bank.branch.platform.common.trace.TraceIdFilter;
import com.bank.branch.platform.common.trace.TraceIdInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Trace 模块自动配置
 * 注册 TraceIdFilter 和 TraceIdInterceptor，实现全链路 traceId 传递
 */
@AutoConfiguration
public class TraceAutoConfiguration implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TraceIdFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    @Bean
    public TraceIdInterceptor traceIdInterceptor() {
        return new TraceIdInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor()).addPathPatterns("/**");
    }
}
