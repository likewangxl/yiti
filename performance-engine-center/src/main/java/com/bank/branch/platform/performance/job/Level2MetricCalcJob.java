package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.service.MetricBatchCalcService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDate;

/**
 * 2级指标批量计算定时任务.
 *
 * <p>不加 {@code @Component}、用 {@code @Autowired} 字段注入：Quartz 经
 * AutowiringSpringBeanJobFactory 反射 newInstance()（需无参构造）后再 autowireBean 注入字段。
 * 构造器注入会使本类无无参构造 → 触发时抛 NoSuchMethodException: &lt;init&gt;()。
 */
@Slf4j
public class Level2MetricCalcJob extends QuartzJobBean {

    @Autowired
    private MetricBatchCalcService metricBatchCalcService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        // 手动触发按指定数据日期；cron 自动触发回退昨日
        LocalDate dataDate = JobTriggerParams.dataDate(context);
        log.info(">>>>>>>>>> 【2级指标定时任务】触发执行，数据日期={} <<<<<<<<<<", dataDate);
        try {
            metricBatchCalcService.execute(2, dataDate);
            log.info(">>>>>>>>>> 【2级指标定时任务】执行完成 <<<<<<<<<<");
        } catch (Exception e) {
            log.error(">>>>>>>>>> 【2级指标定时任务】执行异常: {} <<<<<<<<<<", e.getMessage(), e);
        }
    }
}
