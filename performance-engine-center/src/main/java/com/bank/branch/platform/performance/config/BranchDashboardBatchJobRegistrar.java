package com.bank.branch.platform.performance.config;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.performance.service.BranchDashboardBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 分行批次的声明式治理注册器。
 *
 * <p>启用 profile 时通过公开 {@link JobApi} 幂等 upsert 唯一任务声明；不接触治理 mapper，
 * 也不恢复旧的 MetricSchedulerService.register 或 @Scheduled 路径。cron 最终由治理写入
 * SYS_JOB_CONF，并由 Quartz 包装类执行。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "perf.branch-dashboard", name = "enabled", havingValue = "true")
public class BranchDashboardBatchJobRegistrar {

    private final JobApi jobApi;
    private final BranchDashboardBatchProperties properties;

    public BranchDashboardBatchJobRegistrar(JobApi jobApi,
                                            BranchDashboardBatchProperties properties) {
        this.jobApi = jobApi;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey(BranchDashboardBatchService.TASK_TYPE);
        cmd.setJobName("分行经营大屏不可变批次");
        cmd.setCronExpr(properties.getCron());
        cmd.setQuartzJobClass(
                "com.bank.branch.platform.performance.job.quartz.BranchDashboardBatchQuartzJob");
        cmd.setJobData(Map.of("groupCode", properties.getGroupCode()));
        cmd.setMisfirePolicy("FIRE_ONCE_NOW");
        cmd.setAllowManualTrigger(true);
        cmd.setRemark("分行大屏 TEST/PROD 批次；由 JobApi 注册，Service 负责完整性和原子写入");
        jobApi.registerJob(cmd);
    }
}
