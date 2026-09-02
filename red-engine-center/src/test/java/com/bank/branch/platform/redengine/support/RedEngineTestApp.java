package com.bank.branch.platform.redengine.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 测试专用 Spring Boot 启动类。
 *
 * <p>仅扫描 redengine 子包，避免加载 auth-permission-center / system-governance-center
 * 等被依赖模块无关的 Bean（同 performance-engine-center {@code PerfTestApp} 的隔离模式）。
 * 这两个模块通过各自的 {@code AutoConfiguration.imports} 自动装配的基础设施 Bean
 * （如 PasswordEncoder、MyBatis-Plus 拦截器）不受 scanBasePackages 限制，仍会正常加载。
 */
@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.redengine")
@Import(RedEngineTestConfig.class)
public class RedEngineTestApp {

    public static void main(String[] args) {
        SpringApplication.run(RedEngineTestApp.class, args);
    }
}
