package com.bank.branch.platform.performance.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 缓存启用配置.
 * <p>启用 Spring Cache 注解 (@Cacheable / @CacheEvict), Redis 后端由 bootstrap 的 RedisAutoConfiguration 提供.
 * <p>v1.2: 本模块使用 Redis 做两件事:
 * <ul>
 *   <li>配置表读缓存 (perf:metric_def:*, perf:kpi_scheme:*, perf:sys_control:*, perf:alloc:*)</li>
 *   <li>分布式锁 (perf:sys_control:switch:*, perf:slot-alloc:*)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class PerformanceRedisConfig {
}
