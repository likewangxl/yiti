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
     * Caffeine 缓存管理器（@Primary，由本模块统一持有，整个 Branch Platform 共享）.
     *
     * <p><strong>Dynamic 模式</strong>：未传 cacheNames 列表 → 任意 {@code @Cacheable("xxx")} 用到的
     * cache 名都会按本 Caffeine spec 自动创建。这样 perf-engine（perf:metric_def / perf:target_plan / perf:kpi_scheme）、
     * report-analytics（rpt:dashboard:* 等 7 个）、未来其他模块的 cache 都能复用一份配置。
     *
     * <p>统一策略：TTL 5 分钟 + maxSize 500（仪表盘/元数据查询场景秒级一致性 + 单 cache 500 entries 估算够用）。
     * 如某模块需要更细粒度（不同 TTL / size），届时另起独立 CacheManager（带显式 bean name）+
     * 在 {@code @Cacheable} 上显式 {@code cacheManager="xxx"} 路由即可。
     */
    @Bean("rptCacheManager")
    @Primary
    public CacheManager rptCacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager();   // 无 cacheNames → dynamic, 任意 name 自动创建
        cm.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(500));
        return cm;
    }
}
