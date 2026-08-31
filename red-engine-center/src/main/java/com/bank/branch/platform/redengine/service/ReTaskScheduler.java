package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.redengine.job.ReTaskScheduleJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务域 Quartz 适配器。
 *
 * <p>任务定义不接收用户自定义 jobKey 或 Java 类名。调度注册和执行均只使用本类的
 * 固定白名单常量，并通过治理中心 {@link JobApi} 统一管理 Quartz。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReTaskScheduler {

    /** 任务域唯一调度键，不能从请求参数覆盖。 */
    public static final String JOB_KEY = "RED_ENGINE_TASK_WINDOW";
    /** 每五分钟检查一次当前北京时间窗口。 */
    public static final String CRON = "0 0/5 * * * ?";
    private static final String JOB_NAME = "红色引擎任务窗口补偿";

    private final JobApi jobApi;
    private final ReTaskManagementService managementService;

    /** 注册固定的任务窗口 Quartz Job。 */
    public void register() {
        RegisterJobCmd command = new RegisterJobCmd();
        command.setJobKey(JOB_KEY);
        command.setJobName(JOB_NAME);
        command.setCronExpr(CRON);
        command.setQuartzJobClass(ReTaskScheduleJob.class.getName());
        command.setAllowManualTrigger(false);
        command.setRemark("红色引擎任务域固定窗口补偿，不接受外部 jobKey/class");
        jobApi.registerJob(command);
        log.info("[ReTaskScheduler.register] jobKey={} cron={}", JOB_KEY, CRON);
    }

    /** Quartz Job 的固定执行入口。 */
    public void run() {
        managementService.reconcileCurrentWindows();
    }
}
