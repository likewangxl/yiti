package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标调度补偿检查（V1.7）.
 *
 * <p>启动后延迟 10 分钟首次执行（错峰 syncOnStartup），之后每 10 分钟一次.
 * 用 Spring {@code @Scheduled} 不走 Quartz，避免补偿器自身依赖 Quartz.
 *
 * <p>可通过 {@code perf.scheduler.health-check.enabled=false} 紧急关停（运维开关）；
 * 默认 {@code matchIfMissing=true}，未配置时正常启用.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "perf.scheduler.health-check",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MetricSchedulerHealthCheck {

    private final MetricSchedulerService schedulerService;
    private final MetricDefService metricDefService;
    private final JobApi jobApi;

    /**
     * 每 10 分钟执行一次补偿扫描.
     *
     * <p>initialDelay=600s 确保首次执行在 syncOnStartup 之后（错峰），
     * fixedDelay 保证上一次完成后再计时，避免长扫描与下次触发重叠.
     * 逐个检查 ACTIVE+AUTO 指标是否已在 sys_job_conf 注册；
     * 未注册则重试 register，异常时记录 error 日志不中断扫描.
     */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 600_000L)
    public void runCheck() {
        log.debug("[MetricSchedulerHealth] 开始扫描");
        List<PerfMetricDef> metrics = metricDefService.listSchedulable();
        int repaired = 0;
        for (PerfMetricDef def : metrics) {
            String jobKey = "PERF_METRIC_" + def.getMetricCode();
            if (jobApi.getJobConf(jobKey).isEmpty()) {
                try {
                    schedulerService.register(def);
                    repaired++;
                    log.warn("[MetricSchedulerHealth] 补注册 jobKey={}", jobKey);
                } catch (Exception e) {
                    log.error("[MetricSchedulerHealth] 补注册失败 jobKey={}", jobKey, e);
                }
            }
        }
        if (repaired > 0) {
            log.warn("[MetricSchedulerHealth] 补注册 {} 个指标", repaired);
        }
    }
}
