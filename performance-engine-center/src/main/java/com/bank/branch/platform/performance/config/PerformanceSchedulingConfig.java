package com.bank.branch.platform.performance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * performance-engine-center 调度开关。
 * <p>
 * 通过 {@link EnableScheduling} 在 Spring 上下文中开启 Spring TaskScheduler。
 * V1.8（2026-05-01）由 customer 模块的 {@code CustomerSchedulingConfig}
 * 迁移而来——V1.8 后 customer 模块零 {@code @Scheduled}，故归属正确化。
 * </p>
 * <p>
 * <strong>当前服务于</strong>：
 * {@link com.bank.branch.platform.performance.service.MetricSchedulerHealthCheck}
 * 每 10 分钟兜底巡检（V1.7 spec § 7 论证：纯本地兜底，不交给 Quartz 自调度）。
 * </p>
 * <p>
 * 仅一个空类，不需要 {@code @Bean} 方法 —— 单纯通过 {@code @EnableScheduling}
 * 注解触发 Spring 注册 {@code ScheduledAnnotationBeanPostProcessor}。
 * </p>
 */
@Configuration
@EnableScheduling
public class PerformanceSchedulingConfig {
}
