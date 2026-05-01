package com.bank.branch.platform.customer.job.quartz;

import com.bank.branch.platform.customer.service.LeadCallbackCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Lead 回调补偿 Quartz Job。
 * <p>
 * V1.8（2026-05-01）由 Spring {@code @Scheduled} 迁移而来：
 * <ul>
 *   <li>cron 由 sys_job_conf.cron_expr 控制（默认 {@literal 0 *&#47;5 * * * ?}，每 5 分钟）；</li>
 *   <li>misfire 策略 DO_NOTHING（错过即跳过，下个 5min 继续）；</li>
 *   <li>多实例集群防重由 Quartz QRTZ_LOCKS 行锁兜底（V1.6 已就绪）。</li>
 * </ul>
 * </p>
 * <p>
 * <strong>异常处理</strong>：原 scheduledScan() 顶层 try-catch 移到本类——
 * 补偿任务异常不应冒泡到 Quartz 调度器导致 trigger 被禁用。
 * </p>
 *
 * @see LeadCallbackCompensationService#scanAndCompensate()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadCallbackCompensateQuartzJob implements Job {

    private final LeadCallbackCompensationService compensationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String jobKey = context.getJobDetail().getKey().toString();
        long start = System.currentTimeMillis();
        try {
            compensationService.scanAndCompensate();
        } catch (Exception e) {
            // 顶层兜底：业务异常不冒泡到 Quartz scheduler；
            // JobExecutionLogger（governance V1.6 全局监听器）会记录 vetoed/error 状态。
            log.error("[LeadCallbackCompensateQuartzJob] {} 执行异常", jobKey, e);
        } finally {
            log.info("[LeadCallbackCompensateQuartzJob] {} 完成，耗时 {}ms",
                    jobKey, System.currentTimeMillis() - start);
        }
    }
}
