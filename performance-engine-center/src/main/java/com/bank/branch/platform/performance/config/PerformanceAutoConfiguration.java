package com.bank.branch.platform.performance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * performance-engine-center 模块自动配置入口.
 * <p>通过 @ComponentScan 扫描本模块全部 Bean.
 * <p>当 bootstrap 启动时通过 @SpringBootApplication(scanBasePackages) 直接加载本模块, 无需 @Import.
 * <p>V1.7：启用 Spring @Scheduled 支持（MetricSchedulerHealthCheck 10 分钟补偿扫描）.
 */
@Configuration
@ComponentScan(basePackages = "com.bank.branch.platform.performance")
@EnableConfigurationProperties
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class PerformanceAutoConfiguration {
}
