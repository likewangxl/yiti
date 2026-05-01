package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.SysControlService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 通用指标执行 Quartz 包装类（V1.7）.
 *
 * <p>从 JobDataMap 取 {@code metricCode}，调用 {@link MetricCalcService#calcMetric}。
 * 数据日期固定 T-1，version 取 sys_control 当前 EMP 生效版本（兜底 yyyyMMdd）。
 *
 * <p>不加 {@code @Component}！Quartz 通过反射 newInstance() 创建本对象 →
 * AutowiringSpringBeanJobFactory 完成 {@code @Autowired} 注入
 * （详见 system-governance QuartzConfig）。
 *
 * <p>所有 ACTIVE+AUTO 指标共用本 Job 类，按 jobKey="PERF_METRIC_${metricCode}" 注册到 Quartz；
 * MetricSchedulerService.register 在 sys_job_conf 写入
 * {@code quartzJobClass=本类全限定名 + jobData.metricCode}。
 */
@Slf4j
public class MetricExecuteQuartzJob implements Job {

    @Autowired
    private MetricCalcService metricCalcService;

    @Autowired
    private SysControlService sysControlService;

    /**
     * 执行指标计算.
     *
     * <p>流程：
     * <ol>
     *   <li>从 JobDataMap 读取 metricCode，缺失则抛 {@link JobExecutionException}（不重试）</li>
     *   <li>dataDate = T-1（调度链路不传日期，固定昨日）</li>
     *   <li>version = sys_control EMP 维度生效版本，无则退化 yyyyMMdd</li>
     *   <li>调用 {@link MetricCalcService#calcMetric(String, LocalDate, String, String)}，triggerType="SCHEDULED"</li>
     *   <li>业务异常统一包装为 {@link JobExecutionException}（不重试）</li>
     * </ol>
     *
     * @param context Quartz 执行上下文
     * @throws JobExecutionException metricCode 缺失或业务异常
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String metricCode = context.getMergedJobDataMap().getString("metricCode");
        if (!StringUtils.hasText(metricCode)) {
            throw new JobExecutionException(
                    "metricCode 未传入 JobDataMap, jobKey=" + context.getJobDetail().getKey(), false);
        }
        LocalDate dataDate = LocalDate.now().minusDays(1);
        String version = sysControlService.getActiveVersionOrFallback(dataDate);
        log.info("[MetricExecuteQuartzJob] 开始执行 metricCode={}, dataDate={}, version={}", metricCode, dataDate, version);
        try {
            metricCalcService.calcMetric(metricCode, dataDate, version, "SCHEDULED");
        } catch (Exception e) {
            log.error("[MetricExecuteQuartzJob] metricCode={}, jobKey={} 执行异常",
                    metricCode, context.getJobDetail().getKey(), e);
            throw new JobExecutionException(e, false);
        }
        log.info("[MetricExecuteQuartzJob] 完成 metricCode={}", metricCode);
    }
}
