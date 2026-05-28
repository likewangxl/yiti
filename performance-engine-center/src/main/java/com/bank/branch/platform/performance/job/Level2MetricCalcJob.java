package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.service.MetricBatchCalcService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 2级指标批量计算定时任务
 */
@Slf4j
@Component
public class Level2MetricCalcJob extends QuartzJobBean {

    private final MetricBatchCalcService metricBatchCalcService;

    public Level2MetricCalcJob(MetricBatchCalcService metricBatchCalcService) {
        this.metricBatchCalcService = metricBatchCalcService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate dataDate = LocalDate.now().minusDays(1);
        log.info(">>>>>>>>>> 【2级指标定时任务】触发执行，数据日期={} <<<<<<<<<<", dataDate);
        try {
            metricBatchCalcService.execute(2, dataDate);
            log.info(">>>>>>>>>> 【2级指标定时任务】执行完成 <<<<<<<<<<");
        } catch (Exception e) {
            log.error(">>>>>>>>>> 【2级指标定时任务】执行异常: {} <<<<<<<<<<", e.getMessage(), e);
        }
    }
}
