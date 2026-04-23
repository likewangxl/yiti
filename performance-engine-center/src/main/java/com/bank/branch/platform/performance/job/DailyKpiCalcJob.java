package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.service.KpiCalcService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 每日 KPI 计算定时任务（Task P4.4）.
 *
 * <p>默认 cron：{@code 0 30 1 * * ?}（每天 01:30），对齐计划 P4.4 要求.
 *
 * <p><strong>开关</strong>：{@code perf.job.daily-kpi.enabled=true} 才会启用 Spring Scheduling;
 * <em>默认关闭</em>（{@code matchIfMissing=false}），避免在未显式启用 {@code @EnableScheduling} 的环境下意外执行。
 *
 * <p><strong>流程</strong>：
 * <ol>
 *   <li>查 {@link KpiSchemeService#listActiveSchemes()} 得到全部 ACTIVE 方案</li>
 *   <li>对每个方案调 {@link KpiCalcService#calcScheme}：
 *       <ul>
 *         <li>asOfDate / cycleDate 均取 T-1（假设凌晨跑 T 日任务处理 T-1 数据）</li>
 *         <li>version 以 asOfDate 的 yyyyMMdd 字符串占位；生产环境下建议从 {@code sys_control.active_version} 取</li>
 *       </ul>
 *   </li>
 *   <li>单方案异常 catch 并打 warn 日志，<em>不中断</em>整体循环</li>
 * </ol>
 *
 * <p>V1.2 若需改为 workflow-center 的调度编排 + ShedLock 分布式锁，只需在本 Job 上叠加
 * {@code @SchedulerLock} 注解，不影响业务逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "perf.job.daily-kpi", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class DailyKpiCalcJob {

    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KpiSchemeService kpiSchemeService;
    private final KpiCalcService kpiCalcService;

    /**
     * Spring Scheduler 入口：每日 01:30 触发（cron 可通过 {@code perf.job.daily-kpi.cron} 覆盖）.
     *
     * <p>注意：Spring {@code @Scheduled} 需要全局 {@code @EnableScheduling}；
     * 若未启用（默认状态），本任务只会在被 {@code @ConditionalOnProperty} 启用后才生效。
     */
    @Scheduled(cron = "${perf.job.daily-kpi.cron:0 30 1 * * ?}")
    @SchedulerLock(name = "DailyKpiCalcJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void scheduled() {
        run();
    }

    /**
     * 实际执行体：可被单测直接调用（绕开 Spring 调度器）.
     */
    public void run() {
        List<PerfKpiScheme> schemes = kpiSchemeService.listActiveSchemes();
        if (schemes == null || schemes.isEmpty()) {
            log.info("[DailyKpiCalcJob] 无 ACTIVE KPI 方案，跳过");
            return;
        }

        // T-1 计算口径：凌晨跑 T 日任务处理 T-1 完成的数据
        LocalDate asOfDate = LocalDate.now().minusDays(1);
        LocalDate cycleDate = asOfDate;
        String version = asOfDate.format(VERSION_FMT);

        for (PerfKpiScheme scheme : schemes) {
            try {
                int success = kpiCalcService.calcScheme(
                        scheme.getSchemeCode(),
                        scheme.getCycleType(),
                        cycleDate, asOfDate, version);
                log.info("[DailyKpiCalcJob] scheme={} cycleType={} success={}",
                        scheme.getSchemeCode(), scheme.getCycleType(), success);
            } catch (Exception ex) {
                // 单个方案失败不应影响其它方案：记录异常继续循环
                log.warn("[DailyKpiCalcJob] scheme={} 执行异常: {}",
                        scheme.getSchemeCode(), ex.getMessage());
            }
        }
    }
}
