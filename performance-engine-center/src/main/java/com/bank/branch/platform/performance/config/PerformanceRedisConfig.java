package com.bank.branch.platform.performance.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 绩效模块缓存启用配置（去 Redis 后改 JVM 内存缓存）。
 * <p>去 Redis 背景：行内多实例 docker 部署无 Redis 服务可用。</p>
 * <p>缓存策略变更：
 * <ul>
 *   <li>原 Redis 缓存（5 min TTL，跨实例共享）→ JVM 内存缓存（无 TTL，每实例独立）</li>
 *   <li>@Cacheable / @CacheEvict 注解全部保留生效（Spring Cache 抽象不变）</li>
 *   <li>cacheManager.getCache(...).evict(...) 的 afterCommit 失效代码不变</li>
 *   <li>多实例下：写实例 evict 后本实例立即生效；其他实例缓存条目无变化，
 *       但下次读时由本类 ConcurrentMapCacheManager 的弱一致性接受（配置表低频写，可接受）</li>
 * </ul>
 * </p>
 * <p>预声明所有用到的 cache 名（避免运行时动态创建）：
 * perf:kpi_scheme / perf:metric_def / perf:metric_def:list / perf:target_plan</p>
 */
@Configuration
@EnableCaching
public class PerformanceRedisConfig {

    /**
     * JVM 内存 CacheManager，替代原 RedisCacheManager。
     * <p>预声明 4 个 cache name，对应 KpiApiImpl / MetricApiImpl / TargetApiImpl 的 @Cacheable。
     * 缺少预声明时 ConcurrentMapCacheManager 默认会动态创建，但显式列出更可控。</p>
     */
    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "perf:kpi_scheme",
                "perf:metric_def",
                "perf:metric_def:list",
                "perf:target_plan");
    }
}
