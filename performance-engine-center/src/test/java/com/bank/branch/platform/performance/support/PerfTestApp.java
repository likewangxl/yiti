package com.bank.branch.platform.performance.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 启动类.
 * <p>仅扫描 performance 子包, 避免加载其他模块无关的 Bean.
 */
@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.performance")
public class PerfTestApp {
    public static void main(String[] args) {
        SpringApplication.run(PerfTestApp.class, args);
    }
}
