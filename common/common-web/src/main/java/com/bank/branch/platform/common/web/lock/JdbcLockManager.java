package com.bank.branch.platform.common.web.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 PT_LOCK 表 + SELECT FOR UPDATE 的分布式锁实现。
 * <p>tryLock 用独立事务（REQUIRES_NEW），避免污染业务事务上下文。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcLockManager implements LockManager {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryLock(String key, String holder, long ttlMs) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlMs;

        try {
            Long currentExpires = jdbcTemplate.query(
                    "SELECT EXPIRES_AT FROM PT_LOCK WHERE LOCK_KEY = ? FOR UPDATE",
                    rs -> rs.next() ? rs.getLong(1) : null,
                    key);

            if (currentExpires == null) {
                // 行不存在 → INSERT 占位
                try {
                    jdbcTemplate.update(
                            "INSERT INTO PT_LOCK(LOCK_KEY, HOLDER, ACQUIRED_AT, EXPIRES_AT) VALUES (?, ?, ?, ?)",
                            key, holder, now, expiresAt);
                    return true;
                } catch (DuplicateKeyException e) {
                    // 并发：另一线程刚 INSERT 了，本次失败
                    return false;
                }
            } else if (currentExpires <= now) {
                // 已过期 → UPDATE 强占
                int updated = jdbcTemplate.update(
                        "UPDATE PT_LOCK SET HOLDER = ?, ACQUIRED_AT = ?, EXPIRES_AT = ? WHERE LOCK_KEY = ?",
                        holder, now, expiresAt, key);
                return updated > 0;
            } else {
                // 持有未过期
                return false;
            }
        } catch (DataAccessException e) {
            log.warn("[JdbcLockManager.tryLock] DB 异常 key={} holder={} err={}", key, holder, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean unlock(String key, String holder) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM PT_LOCK WHERE LOCK_KEY = ? AND HOLDER = ?",
                key, holder);
        return deleted > 0;
    }
}
