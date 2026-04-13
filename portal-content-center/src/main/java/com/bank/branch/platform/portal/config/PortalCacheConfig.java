package com.bank.branch.platform.portal.config;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * portal-content-center 缓存常量与工具配置
 *
 * <p>集中管理所有缓存 Key 前缀、默认 TTL 以及防雪崩抖动算法。
 * 遵循 Cache-Aside 模式：读操作缓存未命中时查 DB 并回填；写操作完成后删除缓存。</p>
 *
 * <p>设计依据：docs/modules/portal-content-center/06-并发与事务策略.md §5 Cache-Aside 策略</p>
 */
public final class PortalCacheConfig {

    private PortalCacheConfig() {
        // 工具类，禁止实例化
    }

    /** 模块级缓存 Key 前缀 */
    public static final String CACHE_PREFIX = "portal:";

    /** 产品——"支持中场支持"列表缓存 Key */
    public static final String PRODUCT_SUPPORT_KEY = CACHE_PREFIX + "product:support-available";

    /** 导航——所有启用导航列表缓存 Key */
    public static final String NAV_ACTIVE_KEY = CACHE_PREFIX + "nav:active";

    /** 默认缓存过期时间：5 分钟 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * 在基准 TTL 上叠加 +-10% 随机抖动，防止大量缓存同时过期导致缓存雪崩。
     *
     * @param base 基准过期时长
     * @return 带抖动的过期时长（绝对值不低于 1ms）
     */
    public static Duration jitteredTtl(Duration base) {
        long millis = base.toMillis();
        // +-10% 范围内随机偏移
        long jitter = (long) (millis * 0.1 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        long result = millis + jitter;
        // 保底至少 1ms
        return Duration.ofMillis(Math.max(result, 1));
    }
}
