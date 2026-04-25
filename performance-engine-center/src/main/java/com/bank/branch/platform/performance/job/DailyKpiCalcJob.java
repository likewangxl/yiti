package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.service.KpiCalcService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 每日 KPI 计算业务方法（V1.6 quartz-B 改造）.
 *
 * <p><strong>调度方式</strong>：本类只承载业务逻辑（{@link #run()}），调度由 Quartz 集群（{@code isClustered=true}
 * + JDBC JobStore）通过 Quartz 包装类（见 {@code DailyKpiCalcQuartzJob}）触发。
 * Quartz JDBC JobStore 已提供单一防重，{@code @SchedulerLock} / ShedLock 不再需要。
 *
 * <p><strong>历史</strong>：V1.0-V1.5 时本类持有 {@code @Scheduled} + {@code @SchedulerLock}
 * + {@code @ConditionalOnProperty}，由 Spring Scheduling 触发。V1.6 quartz-B 改造移除上述注解
 * 与 {@code scheduled()} 包装方法，调度统一收敛到 Quartz。
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyKpiCalcJob {

    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KpiSchemeService kpiSchemeService;
    private final KpiCalcService kpiCalcService;

    /**
     * 实际执行体：由 Quartz 包装类调用，也可被单测直接调用.
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
