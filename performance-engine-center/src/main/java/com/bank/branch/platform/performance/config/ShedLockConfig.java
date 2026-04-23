package com.bank.branch.platform.performance.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 分布式锁配置（Task Q5.3）.
 *
 * <p>用途：为 performance-engine-center 的 3 个定时任务（{@link
 * com.bank.branch.platform.performance.job.DailyKpiCalcJob},
 * {@link com.bank.branch.platform.performance.job.SysControlCleanupJob},
 * {@link com.bank.branch.platform.performance.job.PerfRunTaskCleanupJob}）提供基于 Redis 的
 * 分布式互斥锁，防止多节点部署时同一任务被重复执行.
 *
 * <p><strong>锁键命名空间</strong>：Redis 中以 {@code perf:shedlock} 作为 key 前缀，
 * 与其它模块（若未来引入 ShedLock）的锁命名空间隔离.
 *
 * <p><strong>默认锁时长</strong>：{@code defaultLockAtMostFor = "PT10M"} 即 10 分钟，
 * 各 Job 可通过 {@code @SchedulerLock(lockAtMostFor = ...)} 覆盖；lockAtLeastFor 通常设为
 * 比 Job 期望执行时间略小的值，防止意外提前释放.
 *
 * <p><strong>依赖</strong>：{@link RedisConnectionFactory} 由 Spring Boot
 * {@code spring-boot-starter-data-redis} 自动注入；本类不关心底层是 Lettuce 还是 Jedis.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    /**
     * Redis 版 {@link LockProvider} bean.
     *
     * @param redisConnectionFactory Spring Boot 自动注入的 Redis 连接工厂
     * @return 以 {@code perf:shedlock} 为 key 前缀的 Redis LockProvider
     */
    @Bean
    public LockProvider perfLockProvider(RedisConnectionFactory redisConnectionFactory) {
        return new RedisLockProvider(redisConnectionFactory, "perf:shedlock");
    }
}
