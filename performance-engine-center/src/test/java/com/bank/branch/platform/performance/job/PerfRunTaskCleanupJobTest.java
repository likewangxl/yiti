package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfRunTaskCleanupJob 单元测试（Task Q5.2 Red）.
 *
 * <p>职责：每日 03:30（默认 cron）清理 {@code perf_run_task} 表中
 * {@code retention-days}（默认 90 天）之前的 {@code status = SUCCESS} 任务；
 * FAILED / RUNNING / PENDING / PARTIAL / CANCELLED 状态全部保留（失败诊断价值）.
 *
 * <p>Red 阶段：
 * <ul>
 *   <li>{@link PerfRunTaskCleanupJob} 类尚未创建，编译失败即 Red</li>
 *   <li>{@code PerfRunTaskMapper.deleteSuccessTasksBefore(cutoff)} 方法尚未声明</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PerfRunTaskCleanupJobTest {

    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;

    @InjectMocks
    private PerfRunTaskCleanupJob job;

    @BeforeEach
    void setUp() {
        // @Value("${perf.job.run-task-cleanup.retention-days:90}") 在纯 Mockito 场景不会被注入，
        // 测试中通过反射显式设置为 90 以模拟 Spring 属性绑定后的状态.
        ReflectionTestUtils.setField(job, "retentionDays", 90);
    }

    @Test
    @DisplayName("run：按 retentionDays=90 计算 cutoff，只删 SUCCESS 任务")
    void run_deletesOnlySuccessBeforeCutoff() {
        when(perfRunTaskMapper.deleteSuccessTasksBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(5);

        int deleted = job.run();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(perfRunTaskMapper, times(1)).deleteSuccessTasksBefore(captor.capture());
        LocalDateTime cutoff = captor.getValue();

        // cutoff 应落在 now().minusDays(90) 的 ±1 分钟以内（允许用例执行期间的微小时间漂移）
        LocalDateTime expected = LocalDateTime.now().minusDays(90);
        long driftSeconds = Math.abs(ChronoUnit.SECONDS.between(cutoff, expected));
        assertThat(driftSeconds).as("cutoff 应约等于 now().minusDays(90)").isLessThan(60);

        assertThat(deleted).isEqualTo(5);
    }

    @Test
    @DisplayName("run：Mapper 异常时 Job 不抛异常（返回 0），便于调度链路稳定")
    void run_mapperThrows_returnsZero() {
        when(perfRunTaskMapper.deleteSuccessTasksBefore(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("db down"));

        int deleted = job.run();

        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("run：retentionDays 可被覆盖（反射设为 30 天，cutoff 相应前移）")
    void run_customRetentionDays_cutoffMoves() {
        ReflectionTestUtils.setField(job, "retentionDays", 30);
        when(perfRunTaskMapper.deleteSuccessTasksBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        job.run();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(perfRunTaskMapper).deleteSuccessTasksBefore(captor.capture());
        LocalDateTime cutoff = captor.getValue();

        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        long driftSeconds = Math.abs(ChronoUnit.SECONDS.between(cutoff, expected));
        assertThat(driftSeconds).isLessThan(60);
    }
}
