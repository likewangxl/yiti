package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.service.MetricCalcReadyCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * M98 数据就绪协调器 Quartz 包装类（job_key=METRIC_CALC_READY_COORDINATOR）。
 * 部署配置使用每 5 分钟一次、每日 06:00~19:55 的 cron：{@code 0 0/5 6-19 * * ?}。
 *
 * <p>不加 {@code @Component}，由 {@code AutowiringSpringBeanJobFactory} 负责字段注入；
 * {@link DisallowConcurrentExecution} 防止多节点或误触发导致同轮协调重叠。</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class MetricCalcReadyCoordinatorQuartzJob implements Job {

    @Autowired
    private MetricCalcReadyCoordinator coordinator;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            coordinator.run();
        } catch (Exception e) {
            log.error("[MetricCalcReadyCoordinatorQuartzJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
