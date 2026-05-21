package com.bank.branch.platform.common.web.lock;

/**
 * 分布式锁（去 Redis 后基于 PT_LOCK 表 + SELECT FOR UPDATE 实现）。
 * <p>holder 用于 CAS 释放：只有持锁者本人能 unlock，防止误删别人的锁。</p>
 */
public interface LockManager {

    /**
     * 尝试拿锁；不阻塞，立即返回。
     *
     * @param key    业务锁标识（建议命名空间前缀，如 "perf:metric:M_0088"）
     * @param holder 持锁者标识（建议 instanceId + threadId，全局唯一）
     * @param ttlMs  锁租期（毫秒），到期可被别人强占
     * @return true=拿到锁；false=别人持有未过期
     */
    boolean tryLock(String key, String holder, long ttlMs);

    /**
     * 释放锁。CAS：只有 holder 匹配的才删，防误删。
     *
     * @return true=成功释放；false=持锁者不是 holder（说明锁已被强占或已释放）
     */
    boolean unlock(String key, String holder);
}
