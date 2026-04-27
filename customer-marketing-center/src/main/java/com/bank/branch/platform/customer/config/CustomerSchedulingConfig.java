package com.bank.branch.platform.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * customer-marketing-center 调度开关。
 * <p>
 * 通过 {@link EnableScheduling} 在 Spring 上下文中开启全局任务调度能力。
 * 全仓库此前未声明过 {@code @EnableScheduling}，导致 performance 模块的
 * {@code @Scheduled} job 配置类（{@code DailyKpiCalcJob} 等）实际从未被
 * 调度器拉起 —— 本配置开启后，customer + performance 两侧的 {@code @Scheduled}
 * 注解才会真正生效。
 * </p>
 * <p>
 * <strong>FU-14 上下文</strong>：本类为支持 {@code LeadCallbackCompensationService}
 * 5 分钟一次的孤儿 lead 巡检任务而引入；同时顺带让 performance 已存在的 3 个
 * job 配置（默认 {@code @ConditionalOnProperty} 关闭，需要显式 {@code enabled-jobs}
 * 才会生效）具备被调度的物质基础。
 * </p>
 * <p>
 * 仅一个空类，不需要 {@code @Bean} 方法 —— 单纯通过 {@code @EnableScheduling}
 * 注解触发 Spring 注册 {@code ScheduledAnnotationBeanPostProcessor}。
 * </p>
 */
@Configuration
@EnableScheduling
public class CustomerSchedulingConfig {
}
