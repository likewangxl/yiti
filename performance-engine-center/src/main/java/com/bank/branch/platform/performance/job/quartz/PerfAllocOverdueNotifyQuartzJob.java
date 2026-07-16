package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.PerfAllocOverdueNotifyJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 业绩分配调整审批超时提醒 Quartz 包装类（job_key=PERF_ALLOC_OVERDUE_NOTIFY，cron 每天 9 点）.
 *
 * <p>{@link DisallowConcurrentExecution}：同一 JobDetail 不并发执行；集群下配合 QRTZ 行锁，
 * 同一时刻仅一个节点执行（业务已按 overdue_notified_time 幂等，重叠无意义）。
 *
 * <p>不加 @Component！Quartz 反射建实例 + AutowiringSpringBeanJobFactory 注入。execute() 只委托。
 */
@Slf4j
@DisallowConcurrentExecution
public class PerfAllocOverdueNotifyQuartzJob implements Job {

    @Autowired
    private PerfAllocOverdueNotifyJob perfAllocOverdueNotifyJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            perfAllocOverdueNotifyJob.run();
        } catch (Exception e) {
            log.error("[PerfAllocOverdueNotifyQuartzJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
