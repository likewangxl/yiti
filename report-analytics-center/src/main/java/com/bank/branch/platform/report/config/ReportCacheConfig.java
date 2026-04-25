package com.bank.branch.platform.report.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * report-analytics-center Caffeine 缓存配置（Task M2.1.1）.
 *
 * <p>5 个 cache name：
 * <ul>
 *   <li>{@code rpt:dashboard:president}：分行行长仪表盘（M2.2 启用）</li>
 *   <li>{@code rpt:dashboard:org}：机构仪表盘（M2.3 启用）</li>
 *   <li>{@code rpt:dashboard:emp}：员工仪表盘（M2.3 启用）</li>
 *   <li>{@code rpt:metric:tree}：指标树元数据（M2 不直接使用，预留 M3+）</li>
 *   <li>{@code rpt:summary:touch} / {@code rpt:summary:perf} / {@code rpt:summary:cust}：3 类汇总（M3 启用）</li>
 * </ul>
 *
 * <p>统一策略：
 * <ul>
 *   <li>TTL 5 分钟（{@code expireAfterWrite}）— 仪表盘秒级一致性接受</li>
 *   <li>maxSize 500 — 单 cache 最多 500 条 entry（按 dataDate × orgCode 估算够用）</li>
 * </ul>
 *
 * <p>{@code @Primary} 标记本 CacheManager 是 report 模块的唯一可路由 bean，
 * 让 {@code @Cacheable} 默认走本配置；其他模块若引入自己的 CacheManager 不会被此处覆盖.
 */
@Configuration
@EnableCaching
public class ReportCacheConfig {

    /**
     * Caffeine 仪表盘/汇总缓存管理器.
     */
    @Bean("rptCacheManager")
    @Primary
    public CacheManager rptCacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager(
                "rpt:dashboard:president",
                "rpt:dashboard:org",
                "rpt:dashboard:emp",
                "rpt:metric:tree",
                "rpt:summary:touch",
                "rpt:summary:perf",
                "rpt:summary:cust");
        cm.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(500));
        return cm;
    }
}
