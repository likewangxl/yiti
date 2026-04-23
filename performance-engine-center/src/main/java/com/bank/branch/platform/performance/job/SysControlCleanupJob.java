package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.SysControlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * sys_control 历史版本清理定时任务（Task Q5.1）.
 *
 * <p>默认 cron：{@code 0 0 3 * * ?}（每天 03:00），可通过 {@code perf.job.sys-control-cleanup.cron} 覆盖。
 *
 * <p><strong>开关</strong>：{@code perf.job.sys-control-cleanup.enabled=true} 才会注册为 Spring Bean；
 * <em>默认关闭</em>（{@code matchIfMissing=false}），与 {@link DailyKpiCalcJob} 的策略一致，
 * 避免在未显式启用 {@code @EnableScheduling} 的环境下意外执行。
 *
 * <p><strong>保留策略</strong>：
 * <ul>
 *   <li>按 {@code scope_dim} 分组</li>
 *   <li>每个 scope 保留最新 {@code keepCount} 条 {@code is_valid=0} 历史（默认 12，通过
 *       {@code perf.job.sys-control-cleanup.keep-count} 覆盖）</li>
 *   <li>当前生效行 {@code is_valid=1} <strong>永不删除</strong>；SQL 本身已过滤</li>
 *   <li>按 {@code publish_time DESC, id DESC} 排序，OFFSET keepCount 后全部硬删</li>
 * </ul>
 *
 * <p><strong>容错</strong>：单个 scope 异常被 catch 并打 warn，不影响其它 scope 清理循环。
 *
 * <p>V1.2 Q5.3 会叠加 ShedLock {@code @SchedulerLock} 分布式锁，防止多节点重复清理；
 * 本类的 run() 保持无副作用 / 幂等，允许 ShedLock 超时后重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "perf.job.sys-control-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class SysControlCleanupJob {

    private final SysControlMapper sysControlMapper;

    /** 每个 scope 保留的历史版本数量（默认 12）. */
    @Value("${perf.job.sys-control-cleanup.keep-count:12}")
    private int keepCount;

    /**
     * Spring Scheduler 入口：每日 03:00 触发（cron 可通过
     * {@code perf.job.sys-control-cleanup.cron} 覆盖）.
     *
     * <p>注意：Spring {@code @Scheduled} 需要全局 {@code @EnableScheduling}；
     * 若未启用（默认状态），本任务只会在被 {@code @ConditionalOnProperty} 启用后才生效.
     */
    @Scheduled(cron = "${perf.job.sys-control-cleanup.cron:0 0 3 * * ?}")
    @SchedulerLock(name = "SysControlCleanupJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void scheduled() {
        run();
    }

    /**
     * 实际执行体：可被单测直接调用（绕开 Spring 调度器）.
     *
     * @return 本次实际删除的历史版本总行数（跨所有 scope 累加）
     */
    public int run() {
        List<String> scopeDims = sysControlMapper.selectScopeDims();
        if (scopeDims == null || scopeDims.isEmpty()) {
            log.info("[SysControlCleanupJob] 无 scope_dim 记录，跳过");
            return 0;
        }

        int totalDeleted = 0;
        for (String scope : scopeDims) {
            try {
                // 每个 scope 独立事务语义：先查应清理 id，再批量硬删
                List<String> idsToDelete = sysControlMapper.selectOldVersionIdsForCleanup(scope, keepCount);
                if (idsToDelete == null || idsToDelete.isEmpty()) {
                    log.debug("[SysControlCleanupJob] scope={} 无多余历史版本，跳过", scope);
                    continue;
                }
                int deleted = sysControlMapper.deleteByIds(idsToDelete);
                totalDeleted += deleted;
                log.info("[SysControlCleanupJob] scope={} 清理历史版本 {} 条", scope, deleted);
            } catch (Exception ex) {
                // 单 scope 失败不影响其它 scope 循环：记录异常继续
                log.warn("[SysControlCleanupJob] scope={} 清理异常: {}", scope, ex.getMessage());
            }
        }
        log.info("[SysControlCleanupJob] 清理完成，跨 {} 个 scope 共删除 {} 条历史版本",
                scopeDims.size(), totalDeleted);
        return totalDeleted;
    }
}
