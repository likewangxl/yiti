package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.service.KpiScoreCalcService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDate;

/**
 * KPI 分值计算定时任务.
 *
 * <p>默认对"昨日"数据日期、全部 ACTIVE KPI 方案执行计分（schemeCode=null）。
 * 与 Level1/2/3 批量指标计算 job 同构：不加 {@code @Component}、用 {@code @Autowired}
 * 字段注入，Quartz 经 AutowiringSpringBeanJobFactory 反射无参构造后 autowireBean。
 *
 * <p>执行前 {@link KpiScoreCalcService} 会自检"同日 1/2/3 级指标是否都已完成"，
 * 未完成则任务自身置 FAILED 不计分，因此本 job 触发顺序应排在三级指标计算之后。
 */
@Slf4j
public class KpiScoreCalcJob extends QuartzJobBean {

    @Autowired
    private KpiScoreCalcService kpiScoreCalcService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate dataDate = LocalDate.now().minusDays(1);
        log.info(">>>>>>>>>> 【KPI分值定时任务】触发执行，数据日期={} <<<<<<<<<<", dataDate);
        try {
            kpiScoreCalcService.calculate(dataDate, null);
            log.info(">>>>>>>>>> 【KPI分值定时任务】执行完成 <<<<<<<<<<");
        } catch (Exception e) {
            // 任务失败原因已落 PERF_METRIC_CALC_TASK，这里仅记录不再抛（避免 Quartz misfire 重试风暴）
            log.error(">>>>>>>>>> 【KPI分值定时任务】执行异常: {} <<<<<<<<<<", e.getMessage(), e);
        }
    }
}
