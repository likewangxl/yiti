package com.bank.branch.platform.redengine.job;

import com.bank.branch.platform.redengine.service.ReTaskManagementService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 红色引擎任务窗口补偿 Quartz Job。
 * <p>不加 {@code @Component}，由治理中心的 Spring JobFactory 注入服务；固定 JobKey
 * 和类名由 {@code ReTaskScheduler} 注册，避免前端或用户提交任意 Quartz 类。</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class ReTaskScheduleJob implements Job {

    @Autowired
    private ReTaskManagementService managementService;

    /** 扫描并补偿当前北京时间有效窗口。 */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            managementService.reconcileCurrentWindows();
        } catch (Exception exception) {
            log.error("[ReTaskScheduleJob] 任务窗口补偿失败", exception);
            throw new JobExecutionException(exception, false);
        }
    }
}
