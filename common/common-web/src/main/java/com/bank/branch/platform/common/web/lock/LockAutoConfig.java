package com.bank.branch.platform.common.web.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 分布式锁自动配置：注册 JdbcLockManager Bean + 可独立关闭的定时过期锁清理。
 * <p>不在此处 @EnableScheduling —— 各业务模块（如 performance-engine-center）已配；
 * 本类的 @Scheduled 由那里的 TaskScheduler 接管即可。业务锁 Bean 始终装配；
 * {@code platform.lock.cleanup.enabled=false} 仅关闭会写 PT_LOCK 的后台清理，缺省保持启用。</p>
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
public class LockAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(LockAutoConfig.class);

    @Bean
    public LockManager lockManager(JdbcTemplate jdbcTemplate) {
        return new JdbcLockManager(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.lock.cleanup", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public LockCleanupJob lockCleanupJob(JdbcTemplate jdbcTemplate) {
        return new LockCleanupJob(jdbcTemplate);
    }

    /**
     * 定时清理过期锁：fixedDelay 5 分钟，保留 60s grace period 让 unlock CAS 仍能匹配。
     */
    public static class LockCleanupJob {
        private final JdbcTemplate jdbcTemplate;

        public LockCleanupJob(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Scheduled(fixedDelay = 300_000) // 5 分钟
        public void cleanExpired() {
            long threshold = System.currentTimeMillis() - 60_000;
            int deleted = jdbcTemplate.update("DELETE FROM PT_LOCK WHERE EXPIRES_AT < ?", threshold);
            if (deleted > 0) {
                log.debug("[LockCleanupJob] 清理过期锁 deleted={}", deleted);
            }
        }
    }
}
