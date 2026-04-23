package com.bank.branch.platform.performance.config;

import com.bank.branch.platform.performance.job.DailyKpiCalcJob;
import com.bank.branch.platform.performance.job.PerfRunTaskCleanupJob;
import com.bank.branch.platform.performance.job.SysControlCleanupJob;
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
 *   <li>3 个 Job（DailyKpiCalc / SysControlCleanup / PerfRunTaskCleanup）的
 *       {@code scheduled()} 方法都必须标注 {@link SchedulerLock}，且 {@code name} 属性唯一</li>
 * </ul>
 *
 * <p>Red 阶段：
 * <ul>
 *   <li>{@link ShedLockConfig} 类尚未创建，编译失败即 Red</li>
 *   <li>3 个 Job 的 scheduled() 方法尚未加 {@code @SchedulerLock}，反射断言失败</li>
 * </ul>
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
    @DisplayName("DailyKpiCalcJob.scheduled 必须标注 @SchedulerLock(name=DailyKpiCalcJob)")
    void dailyKpiCalcJob_scheduledIsAnnotatedWithSchedulerLock() throws Exception {
        SchedulerLock ann = getSchedulerLock(DailyKpiCalcJob.class, "scheduled");
        assertThat(ann).as("DailyKpiCalcJob.scheduled 应加 @SchedulerLock").isNotNull();
        assertThat(ann.name()).isEqualTo("DailyKpiCalcJob");
    }

    @Test
    @DisplayName("SysControlCleanupJob.scheduled 必须标注 @SchedulerLock(name=SysControlCleanupJob)")
    void sysControlCleanupJob_scheduledIsAnnotatedWithSchedulerLock() throws Exception {
        SchedulerLock ann = getSchedulerLock(SysControlCleanupJob.class, "scheduled");
        assertThat(ann).as("SysControlCleanupJob.scheduled 应加 @SchedulerLock").isNotNull();
        assertThat(ann.name()).isEqualTo("SysControlCleanupJob");
    }

    @Test
    @DisplayName("PerfRunTaskCleanupJob.scheduled 必须标注 @SchedulerLock(name=PerfRunTaskCleanupJob)")
    void perfRunTaskCleanupJob_scheduledIsAnnotatedWithSchedulerLock() throws Exception {
        SchedulerLock ann = getSchedulerLock(PerfRunTaskCleanupJob.class, "scheduled");
        assertThat(ann).as("PerfRunTaskCleanupJob.scheduled 应加 @SchedulerLock").isNotNull();
        assertThat(ann.name()).isEqualTo("PerfRunTaskCleanupJob");
    }

    @Test
    @DisplayName("3 个 Job 的 lock name 两两唯一，避免多 Job 互相阻塞")
    void allThreeJobs_lockNamesAreDistinct() throws Exception {
        String n1 = getSchedulerLock(DailyKpiCalcJob.class, "scheduled").name();
        String n2 = getSchedulerLock(SysControlCleanupJob.class, "scheduled").name();
        String n3 = getSchedulerLock(PerfRunTaskCleanupJob.class, "scheduled").name();
        assertThat(n1).isNotEqualTo(n2);
        assertThat(n1).isNotEqualTo(n3);
        assertThat(n2).isNotEqualTo(n3);
    }

    private SchedulerLock getSchedulerLock(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(methodName);
        return m.getAnnotation(SchedulerLock.class);
    }
}
