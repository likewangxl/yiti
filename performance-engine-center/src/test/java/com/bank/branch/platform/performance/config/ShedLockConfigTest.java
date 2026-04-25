package com.bank.branch.platform.performance.config;

import com.bank.branch.platform.performance.job.PerfRunTaskCleanupJob;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * ShedLockConfig 单元测试（Task Q5.3 Red）.
 *
 * <p>职责：
 * <ul>
 *   <li>{@link ShedLockConfig} 能通过注入 {@link RedisConnectionFactory} 构造出
 *       非空的 {@link LockProvider} bean（用 Mockito 模拟 Redis 连接，避免对真实 Redis 依赖）</li>
 *   <li>剩余 Job（PerfRunTaskCleanup）的 {@code scheduled()} 方法都必须标注
 *       {@link SchedulerLock}，且 {@code name} 属性唯一</li>
 * </ul>
 *
 * <p>V1.6 quartz-B 改造说明（2026-04-25）：DailyKpiCalcJob (P2.1) / SysControlCleanupJob (P2.2)
 * 的 {@code scheduled()} 已删除（改由 Quartz 调度器调用裸业务方法），相关断言已移除；
 * PerfRunTaskCleanupJob 将在 P2.3 同步移除，整个 {@link ShedLockConfig} + 本测试将在 P4.1 一并退役。
 *
 * <p>注：Redis 未启动的本地环境下，本测试通过 Mockito mock {@link RedisConnectionFactory}
 * 验证 bean 装配；互斥语义的真实 Redis 测试留给集成测试环境（ShedLockConfig 使用
 * {@code net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider}，
 * 其自身的 Redis INCR/DEL 互斥语义由上游依赖 5.13.0 保证）.
 */
class ShedLockConfigTest {

    @Test
    @DisplayName("ShedLockConfig.perfLockProvider 注入 RedisConnectionFactory 返回非空 LockProvider")
    void perfLockProvider_returnsNonNull() {
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        ShedLockConfig config = new ShedLockConfig();

        LockProvider provider = config.perfLockProvider(mockFactory);

        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("PerfRunTaskCleanupJob.scheduled 必须标注 @SchedulerLock(name=PerfRunTaskCleanupJob)")
    void perfRunTaskCleanupJob_scheduledIsAnnotatedWithSchedulerLock() throws Exception {
        SchedulerLock ann = getSchedulerLock(PerfRunTaskCleanupJob.class, "scheduled");
        assertThat(ann).as("PerfRunTaskCleanupJob.scheduled 应加 @SchedulerLock").isNotNull();
        assertThat(ann.name()).isEqualTo("PerfRunTaskCleanupJob");
    }

    private SchedulerLock getSchedulerLock(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(methodName);
        return m.getAnnotation(SchedulerLock.class);
    }
}
