package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.PerfRunTaskCleanupJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * perf_run_task 过期任务清理 Quartz 包装类（V1.6 quartz 整合引入）.
 *
 * <p>不加 @Component！Quartz 通过反射 newInstance() 创建本对象 →
 * AutowiringSpringBeanJobFactory 完成 @Autowired 注入（详见 system-governance QuartzConfig）.
 *
 * <p>execute() 仅作业务方法委托，不持有任何业务逻辑.
 */
@Slf4j
public class PerfRunTaskCleanupQuartzJob implements Job {

    @Autowired
    private PerfRunTaskCleanupJob perfRunTaskCleanupJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            perfRunTaskCleanupJob.run();   // 返回 int，调度链路不消费 → 显式丢弃
        } catch (Exception e) {
            log.error("[PerfRunTaskCleanupQuartzJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
