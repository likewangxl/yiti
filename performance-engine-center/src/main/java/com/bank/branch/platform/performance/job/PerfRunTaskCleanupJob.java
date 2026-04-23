package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * perf_run_task 过期任务清理定时任务（Task Q5.2）.
 *
 * <p>默认 cron：{@code 0 30 3 * * ?}（每天 03:30），可通过
 * {@code perf.job.run-task-cleanup.cron} 覆盖；与 {@link SysControlCleanupJob} 错开半小时，
 * 避免两个清理任务在同一瞬间压库.
 *
 * <p><strong>开关</strong>：{@code perf.job.run-task-cleanup.enabled=true} 才会注册为 Spring Bean；
 * <em>默认关闭</em>（{@code matchIfMissing=false}），避免未显式启用 {@code @EnableScheduling} 时意外执行.
 *
 * <p><strong>清理策略</strong>：
 * <ul>
 *   <li>按 {@code perf.job.run-task-cleanup.retention-days}（默认 90）计算 cutoff = now − N 天</li>
 *   <li>仅删 {@code status = SUCCESS} 且 {@code end_time &lt; cutoff} 的行</li>
 *   <li>FAILED / RUNNING / PENDING / PARTIAL / CANCELLED 全部保留（失败诊断 / 异常回溯价值）</li>
 *   <li>RUNNING 任务的 {@code end_time} 为 NULL，SQL 过滤自然不会命中，不需要在 Job 层额外兜底</li>
 * </ul>
 *
 * <p><strong>容错</strong>：Mapper 异常被 catch 并打 warn，返回 0；不向上传播以保持调度链路稳定，
 * 下一周期继续重试即可.
 *
 * <p>V1.2 Q5.3 会叠加 ShedLock {@code @SchedulerLock} 分布式锁，防止多节点重复清理.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "perf.job.run-task-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class PerfRunTaskCleanupJob {

    private final PerfRunTaskMapper perfRunTaskMapper;

    /** 保留天数（默认 90 天）. */
    @Value("${perf.job.run-task-cleanup.retention-days:90}")
    private int retentionDays;

    /**
     * Spring Scheduler 入口：每日 03:30 触发（cron 可通过
     * {@code perf.job.run-task-cleanup.cron} 覆盖）.
     */
    @Scheduled(cron = "${perf.job.run-task-cleanup.cron:0 30 3 * * ?}")
    public void scheduled() {
        run();
    }

    /**
     * 实际执行体：可被单测直接调用（绕开 Spring 调度器）.
     *
     * @return 实际删除的任务行数；Mapper 异常时返回 0
     */
    public int run() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        try {
            int deleted = perfRunTaskMapper.deleteSuccessTasksBefore(cutoff);
            log.info("[PerfRunTaskCleanupJob] cutoff={} retentionDays={} 删除 SUCCESS 任务 {} 条",
                    cutoff, retentionDays, deleted);
            return deleted;
        } catch (Exception ex) {
            // 数据库异常不向上传播，记录告警由下一周期重试：Q5.3 ShedLock 也依赖这一幂等语义
            log.warn("[PerfRunTaskCleanupJob] cutoff={} 清理异常: {}", cutoff, ex.getMessage());
            return 0;
        }
    }
}
