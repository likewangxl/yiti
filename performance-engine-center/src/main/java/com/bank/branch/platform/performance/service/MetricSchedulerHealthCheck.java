package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标调度补偿检查（V1.7）.
 *
 * <p>每 10 分钟扫描"应注册但 sys_job_conf 缺失"的指标，重试 register.
 * 用 Spring {@code @Scheduled} 不走 Quartz，避免补偿器自身依赖 Quartz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricSchedulerHealthCheck {

    private final MetricSchedulerService schedulerService;
    private final MetricDefService metricDefService;
    private final JobApi jobApi;

    /**
     * 每 10 分钟执行一次补偿扫描.
     *
     * <p>逐个检查 ACTIVE+AUTO 指标是否已在 sys_job_conf 注册；
     * 未注册则重试 register，异常时记录 error 日志不中断扫描.
     */
    @Scheduled(cron = "0 */10 * * * ?")
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
