package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.service.MetricBatchCalcService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDate;

/**
 * 1级指标批量计算定时任务.
 *
 * <p>不加 {@code @Component}、用 {@code @Autowired} 字段注入：Quartz 经
 * AutowiringSpringBeanJobFactory 反射 newInstance()（需无参构造）后再 autowireBean 注入字段。
 * 历史上误用构造器注入（无无参构造），导致每次触发抛 NoSuchMethodException: &lt;init&gt;()、
 * 触发器进 ERROR、从未成功调起。对齐 MetricExecuteQuartzJob 写法。
 */
@Slf4j
public class Level1MetricCalcJob extends QuartzJobBean {

    @Autowired
    private MetricBatchCalcService metricBatchCalcService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate dataDate = LocalDate.now().minusDays(1);
        log.info(">>>>>>>>>> 【1级指标定时任务】触发执行，数据日期={} <<<<<<<<<<", dataDate);
        try {
            metricBatchCalcService.execute(1, dataDate);
            log.info(">>>>>>>>>> 【1级指标定时任务】执行完成 <<<<<<<<<<");
        } catch (Exception e) {
            log.error(">>>>>>>>>> 【1级指标定时任务】执行异常: {} <<<<<<<<<<", e.getMessage(), e);
        }
    }
}
