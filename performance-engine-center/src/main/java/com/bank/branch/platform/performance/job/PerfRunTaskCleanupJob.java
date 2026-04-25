package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * perf_run_task 过期任务清理业务逻辑（Task Q5.2）.
 *
 * <p><strong>V1.6 quartz-B 重构（2026-04-25, P2.3）</strong>：删除 Spring `@Scheduled`
 * / `@ConditionalOnProperty` 注解与 `scheduled()` 包装方法，调度改由 Quartz
 * 调度器（quartz-B 子项目交付）调用裸 {@link #run()}。本类仅保留业务执行体，分布式互斥语义
 * 由 Quartz JobStoreCMT/JDBC-JobStore 通过 QRTZ_LOCKS 行锁提供。
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerfRunTaskCleanupJob {

    private final PerfRunTaskMapper perfRunTaskMapper;

    /** 保留天数（默认 90 天）. */
    @Value("${perf.job.run-task-cleanup.retention-days:90}")
    private int retentionDays;

    /**
     * 实际执行体：可被单测直接调用，亦由 Quartz 调度器（quartz-B）调用.
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
            // 数据库异常不向上传播，记录告警由下一周期重试：Quartz 调度器幂等触发依赖此契约
            log.warn("[PerfRunTaskCleanupJob] cutoff={} 清理异常: {}", cutoff, ex.getMessage());
            return 0;
        }
    }
}
