package com.bank.branch.platform.common.web.config;

import com.bank.branch.platform.common.web.CorsConfig;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Web 模块自动配置
 * 通过 Spring Boot AutoConfiguration 机制自动注册全局异常处理和 CORS 配置
 */
@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorsConfig.class})
public class WebAutoConfiguration {
}
