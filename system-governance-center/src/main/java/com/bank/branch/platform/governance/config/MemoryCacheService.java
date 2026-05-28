package com.bank.branch.platform.governance.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 内存缓存服务（替代 Redis，开发 / 联调环境用）
 *
 * <p>使用 Caffeine 实现，固定 maximumSize=10000，expireAfterWrite=1 小时。
 * put 方法接收 ttl 参数但忽略（单个 Cache 实例不支持 per-entry TTL），
 * 1 小时过期对开发环境已足够。</p>
 */
@Service
public class MemoryCacheService {

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    /**
     * 读取缓存值，不存在时返回 null。
     *
     * @param key 缓存键
     * @return 缓存值，未命中返回 null
     */
    public Object get(String key) {
        return cache.getIfPresent(key);
    }

    /**
     * 写入缓存。ttl 参数保留以匹配原 RedisTemplate 调用签名，实际使用全局 1 小时过期。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   期望过期时长（当前实现忽略，全局 expireAfterWrite=1h 生效）
     */
    public void put(String key, Object value, Duration ttl) {
        cache.put(key, value);
    }

    /**
     * 删除指定缓存键。
     *
     * @param key 缓存键
     */
    public void evict(String key) {
        cache.invalidate(key);
    }
}
