package com.bank.branch.platform.customer.config;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * customer-marketing-center 缓存常量与工具配置。
 * <p>
 * 集中管理所有缓存 Key 前缀、默认 TTL 以及防雪崩抖动算法。
 * 遵循 Cache-Aside 模式：读操作缓存未命中时查 DB 并回填；写操作完成后删除缓存。
 * </p>
 *
 * <p>预留 Redis 缓存配置位，后续可对以下场景开启缓存：
 * <ul>
 *   <li>标签列表（listEnabled）：高频只读，TTL 5 分钟</li>
 *   <li>客户主档详情（getCustomer）：中频只读，TTL 5 分钟</li>
 * </ul>
 * </p>
 */
public final class CustomerCacheConfig {

    private CustomerCacheConfig() {
        // 工具类，禁止实例化
    }

    /** 模块级缓存 Key 前缀 */
    public static final String CACHE_PREFIX = "customer:";

    /** 标签列表缓存 Key（启用状态标签，供打标选择使用） */
    public static final String TAG_ENABLED_KEY = CACHE_PREFIX + "tag:enabled";

    /** 客户主档详情缓存 Key 前缀（后缀为 custId） */
    public static final String CUSTOMER_DETAIL_KEY_PREFIX = CACHE_PREFIX + "master:";

    /** 标签列表缓存 TTL：5 分钟 */
    public static final Duration TAG_LIST_TTL = Duration.ofMinutes(5);

    /** 客户详情缓存 TTL：5 分钟 */
    public static final Duration CUSTOMER_DETAIL_TTL = Duration.ofMinutes(5);

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
