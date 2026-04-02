package com.bank.branch.platform.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证模块核心配置
 * 提供密码编码器和 ObjectMapper Bean，供 AuthService、AuthenticationFilter 等组件使用。
 */
@Configuration
public class AuthConfig {

    /**
     * BCrypt 密码编码器
     * 用于登录时密码校验及新用户密码加密
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Jackson ObjectMapper（注册 JavaTimeModule，支持 LocalDateTime 序列化）
     * 供 Filter/Interceptor 写 JSON 响应体使用
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
