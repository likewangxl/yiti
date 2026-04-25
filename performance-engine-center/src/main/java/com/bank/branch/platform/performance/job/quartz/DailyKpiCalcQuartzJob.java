package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.DailyKpiCalcJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 日常 KPI 计算 Quartz 包装类（V1.6 quartz 整合引入）.
 *
 * <p>不加 @Component！Quartz 通过反射 newInstance() 创建本对象 →
 * AutowiringSpringBeanJobFactory 完成 @Autowired 注入（详见 system-governance QuartzConfig）.
 *
 * <p>execute() 仅作业务方法委托，不持有任何业务逻辑.
 */
@Slf4j
public class DailyKpiCalcQuartzJob implements Job {

    @Autowired
    private DailyKpiCalcJob dailyKpiCalcJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            dailyKpiCalcJob.run();   // void 返回
        } catch (Exception e) {
            log.error("[DailyKpiCalcQuartzJob] 执行异常，jobKey={}",
                context.getJobDetail().getKey(), e);
            throw new JobExecutionException(e, false);   // false = 不立即重试
        }
    }
}
