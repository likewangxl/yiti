package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.StatShowArchiveJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 统计展示表旬度归档 Quartz 包装类（job_key=STAT_SHOW_ARCHIVE，cron 每天 6:30~18:30 循环）.
 *
 * <p>{@link DisallowConcurrentExecution}：同一 JobDetail 不并发执行——防同一天 13 次触发/多节点重叠
 * （单次可能耗时较长，且业务已幂等，重叠无意义）。集群下配合 QRTZ 行锁，同一时刻仅一个节点执行。
 *
 * <p>不加 @Component！Quartz 反射建实例 + AutowiringSpringBeanJobFactory 注入。execute() 只委托。
 */
@Slf4j
@DisallowConcurrentExecution
public class StatShowArchiveQuartzJob implements Job {

    @Autowired
    private StatShowArchiveJob statShowArchiveJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            statShowArchiveJob.run();
        } catch (Exception e) {
            log.error("[StatShowArchiveQuartzJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
