package com.bank.branch.platform.bizapp.config;

/**
 * 业务申请中心缓存配置。
 * <p>
 * Cache-Aside 模式，所有 Key 前缀 {@code bizapp:}，
 * 默认 TTL 5 分钟 + 10% 随机抖动防雪崩。
 * </p>
 */
public class BizAppCacheConfig {

    /** 所有缓存 Key 的统一前缀 */
    public static final String KEY_PREFIX = "bizapp:";

    /** 默认缓存 TTL，单位秒（5 分钟） */
    public static final long DEFAULT_TTL_SECONDS = 300;

    /**
     * 随机抖动比例（10%）。
     * <p>
     * 实际 TTL = DEFAULT_TTL_SECONDS * (1 + JITTER_RATIO * random)，
     * 防止缓存集中过期引发雪崩。
     * </p>
     */
    public static final double JITTER_RATIO = 0.1;

    private BizAppCacheConfig() {
        // 工具类，禁止实例化
    }
}
