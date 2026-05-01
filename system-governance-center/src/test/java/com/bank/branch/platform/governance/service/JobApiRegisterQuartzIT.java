package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.governance.config.QuartzConfig;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.listener.JobExecutionLogger;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * V1.7：JobApi.registerJob/unregisterJob 真实 Quartz 集成 IT.
 *
 * <p>验证：sys_job_conf upsert + Scheduler 注入 + 反向幂等清理 整个端到端链路.
 *
 * <p>策略（同 {@link JobServiceQuartzIntegrationIT}）：
 * <ul>
 *   <li>最小 SpringBoot TestApp，@Import 显式装配 QuartzConfig + JobExecutionLogger + JobService</li>
 *   <li>job-store-type=memory（RAMJobStore），不依赖 QRTZ_* 表与真实 DataSource</li>
 *   <li>JobConfMapper / JobRunLogMapper 通过 @MockBean 注入</li>
 * </ul>
 */
@SpringBootTest(classes = JobApiRegisterQuartzIT.JobApiRegisterITApp.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.quartz.job-store-type=memory",
        "spring.quartz.startup-delay=0s",
        "spring.sql.init.mode=never"
})
class JobApiRegisterQuartzIT {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JobService jobService;

    @MockBean
    private JobConfMapper jobConfMapper;

    @MockBean
    private JobRunLogMapper jobRunLogMapper;

    private static final String IT_JOB_KEY = "IT_TEST_REGISTER_JOB";

    @AfterEach
    void cleanup() {
        // 兜底清理（即使测试失败也不留垃圾）
        try {
            jobService.unregisterJob(IT_JOB_KEY);
        } catch (Exception ignored) {
            // 幂等：不存在时静默
        }
    }

    /**
     * 验证 registerJob 写入 sys_job_conf（mock insert）并将 JobDetail 注入真实 Quartz Scheduler.
     */
    @Test
    void registerJob_writes_sys_job_conf_and_schedules_in_quartz() throws Exception {
        // given：jobKey 不存在，触发 insert 路径
        when(jobConfMapper.selectByJobKey(IT_JOB_KEY)).thenReturn(null);

        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey(IT_JOB_KEY);
        cmd.setJobName("V1.7 IT 测试任务");
        cmd.setCronExpr("0 0 2 * * ?");
        cmd.setQuartzJobClass(NoOpJob.class.getName());
        cmd.setMisfirePolicy("FIRE_ONCE_NOW");
        cmd.setJobData(Map.of("metricCode", "M_IT_TEST"));

        // when
        String id = jobService.registerJob(cmd);

        // then - 返回 id 不为空
        assertThat(id).isNotBlank();

        // then - 真实 Quartz Scheduler 已注入 JobKey（DEFAULT 组，因 jobKey 不以 PERF_METRIC_ 开头）
        assertThat(scheduler.checkExists(JobKey.jobKey(IT_JOB_KEY, "DEFAULT"))).isTrue();
    }

    /**
     * 验证 registerJob（insert 路径）后 unregisterJob 清除 Quartz JobDetail.
     */
    @Test
    void unregisterJob_removes_from_quartz() throws Exception {
        // given：先注册
        when(jobConfMapper.selectByJobKey(IT_JOB_KEY)).thenReturn(null);

        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey(IT_JOB_KEY);
        cmd.setJobName("V1.7 IT 注销测试任务");
        cmd.setCronExpr("0 0 3 * * ?");
        cmd.setQuartzJobClass(NoOpJob.class.getName());
        cmd.setMisfirePolicy("FIRE_ONCE_NOW");

        jobService.registerJob(cmd);
        assertThat(scheduler.checkExists(JobKey.jobKey(IT_JOB_KEY, "DEFAULT"))).isTrue();

        // when：注销（mock selectByJobKey 返回已存在条目，使 delete 路径可找到 conf）
        SysJobConf existing = new SysJobConf();
        existing.setId("IT_ID_001");
        existing.setJobKey(IT_JOB_KEY);
        when(jobConfMapper.selectByJobKey(IT_JOB_KEY)).thenReturn(existing);

        jobService.unregisterJob(IT_JOB_KEY);

        // then - Scheduler 中 JobKey 已被删除
        assertThat(scheduler.checkExists(JobKey.jobKey(IT_JOB_KEY, "DEFAULT"))).isFalse();
    }

    /**
     * 验证 registerJob overwrite（update）路径：jobKey 已存在时走 updateById 分支并覆盖 Scheduler.
     */
    @Test
    void registerJob_overwrite_updates_scheduler_cron() throws Exception {
        // given：先注册旧 cron
        when(jobConfMapper.selectByJobKey(IT_JOB_KEY)).thenReturn(null);
        RegisterJobCmd cmdV1 = new RegisterJobCmd();
        cmdV1.setJobKey(IT_JOB_KEY);
        cmdV1.setJobName("V1 任务");
        cmdV1.setCronExpr("0 0 1 * * ?");
        cmdV1.setQuartzJobClass(NoOpJob.class.getName());
        cmdV1.setMisfirePolicy("FIRE_ONCE_NOW");
        jobService.registerJob(cmdV1);

        // Scheduler 中已存在
        assertThat(scheduler.checkExists(JobKey.jobKey(IT_JOB_KEY, "DEFAULT"))).isTrue();

        // 先删除旧 job 模拟覆盖场景（RAMJobStore 不自动覆盖，参考 rescheduleJob_updatesCron）
        scheduler.deleteJob(JobKey.jobKey(IT_JOB_KEY, "DEFAULT"));

        // given：overwrite，mock 返回已有 conf
        SysJobConf existing = new SysJobConf();
        existing.setId("IT_ID_V1");
        existing.setJobKey(IT_JOB_KEY);
        existing.setCronExpr("0 0 1 * * ?");
        when(jobConfMapper.selectByJobKey(IT_JOB_KEY)).thenReturn(existing);

        RegisterJobCmd cmdV2 = new RegisterJobCmd();
        cmdV2.setJobKey(IT_JOB_KEY);
        cmdV2.setJobName("V2 任务");
        cmdV2.setCronExpr("0 30 4 * * ?");
        cmdV2.setQuartzJobClass(NoOpJob.class.getName());
        cmdV2.setMisfirePolicy("FIRE_ONCE_NOW");

        // when
        String returnedId = jobService.registerJob(cmdV2);

        // then - 返回旧 id（update 路径）
        assertThat(returnedId).isEqualTo("IT_ID_V1");
        // Scheduler 中重新注入
        assertThat(scheduler.checkExists(JobKey.jobKey(IT_JOB_KEY, "DEFAULT"))).isTrue();
    }

    // ── 测试专用 SpringBoot 启动类 ─────────────────────────────────────────────

    /**
     * 最小 TestApp，与 {@link JobServiceQuartzIntegrationIT.JobServiceITApp} 同模式.
     *
     * <p>scanBasePackages 指向不存在子包，关闭 component scan，@Import 显式装入目标类.
     */
    @SpringBootApplication(scanBasePackages = "com.bank.branch.platform.governance.service.itscope.notexist")
    @Import({QuartzConfig.class, JobExecutionLogger.class, JobService.class})
    static class JobApiRegisterITApp {
    }

    // ── 测试用空操作 Job ───────────────────────────────────────────────────────

    /**
     * 测试用空操作 Job（不需要业务逻辑）.
     */
    public static class NoOpJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            // no-op
        }
    }
}
