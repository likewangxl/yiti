package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.redengine.job.ReTaskScheduleJob;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务域 Quartz 适配器。
 *
 * <p>任务定义不接收用户自定义 jobKey 或 Java 类名。调度注册和执行均只使用本类的
 * 固定白名单常量，并通过治理中心 {@link JobApi} 统一管理 Quartz。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.quartz", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReTaskScheduler {

    /** 任务域唯一调度键，不能从请求参数覆盖。 */
    public static final String JOB_KEY = "RED_ENGINE_TASK_WINDOW";
    /** 每五分钟检查一次当前北京时间窗口。 */
    public static final String CRON = "0 0/5 * * * ?";
    private static final String JOB_NAME = "红色引擎任务窗口补偿";

    private final JobApi jobApi;
    private final ReTaskManagementService managementService;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * 应用就绪后注册任务窗口 Quartz Job。
     *
     * <p>同一应用上下文可能收到重复的就绪事件，使用进程内门闩避免重复调用；跨进程/重启时
     * {@link JobApi#registerJob(RegisterJobCmd)} 按固定 jobKey 覆盖，保证数据库和 Quartz 配置幂等。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        register();
    }

    /** 注册固定的任务窗口 Quartz Job；重复调用在当前应用实例内幂等。 */
    public void register() {
        if (!registered.compareAndSet(false, true)) {
            log.debug("[ReTaskScheduler.register] 已注册，跳过重复调用 jobKey={}", JOB_KEY);
            return;
        }
        try {
            registerFixedJob();
        } catch (RuntimeException exception) {
            // 注册失败允许下一次就绪事件或健康恢复流程重试，不能把失败状态永久锁死。
            registered.set(false);
            throw exception;
        }
    }

    /** 应用关闭时只注销本任务域固定 jobKey；未成功注册时不对治理中心发起外联。 */
    @PreDestroy
    public void onShutdown() {
        if (!registered.compareAndSet(true, false)) {
            log.debug("[ReTaskScheduler.onShutdown] 未注册，跳过注销 jobKey={}", JOB_KEY);
            return;
        }
        jobApi.unregisterJob(JOB_KEY);
        log.info("[ReTaskScheduler.onShutdown] 已注销 jobKey={}", JOB_KEY);
    }

    /** 组装并提交固定白名单 Job 配置，禁止外部覆盖 key/class。 */
    private void registerFixedJob() {
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
