package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.config.QuartzConfig;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.entity.SysJobRunLog;
import com.bank.branch.platform.governance.listener.JobExecutionLogger;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobService 集成测试（V1.6 quartz 整合 P3.4）.
 *
 * <p>验证 JobService 与真实 Quartz Scheduler 的端到端联动：syncJobsOnStartup 注册 JobDetail/Trigger
 * 到 Scheduler、cron 变更后重启可覆盖、triggerJob 通过真实 Scheduler 触发立即执行。
 *
 * <p>策略（参考 P1.6 的 {@link com.bank.branch.platform.governance.config.QuartzConfigIntegrationIT}）：
 * <ul>
 *   <li>使用最小 Test SpringBootApplication 作为 @SpringBootTest 根，通过 @Import 显式装配
 *       QuartzConfig + AutowiringSpringBeanJobFactory + JobExecutionLogger + JobService</li>
 *   <li>job-store-type=memory（RAMJobStore），不依赖 QRTZ_* 表与真实 DataSource</li>
 *   <li>JobConfMapper / JobRunLogMapper 通过 @MockBean 注入</li>
 *   <li>JobService 复用 @PostConstruct.syncJobsOnStartup 进入 Scheduler，验证真实 Scheduler 状态</li>
 * </ul>
 *
 * <p>约束：本测试仅验证 schedule/trigger 三类核心场景，misfire 策略单测见 JobServiceMisfirePolicyTest，
 * pause/resume 单测见 JobServiceTest（mock Scheduler 路径）.
 */
@SpringBootTest(classes = JobServiceQuartzIntegrationIT.JobServiceITApp.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.quartz.job-store-type=memory",
        "spring.quartz.startup-delay=0s",
        // 跳过 src/test/resources/schema.sql 初始化，本测试不需要 governance 业务表
        "spring.sql.init.mode=never"
})
class JobServiceQuartzIntegrationIT {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JobService jobService;

    @MockBean
    private JobConfMapper jobConfMapper;

    @MockBean
    private JobRunLogMapper jobRunLogMapper;

    /**
     * 场景 1：syncJobsOnStartup 注册 ACTIVE 任务到真实 Scheduler.
     *
     * <p>因 @PostConstruct 在 Spring 启动时已经触发了一次 syncJobsOnStartup（mock 默认返回空），
     * 此处 reset mock 后显式调用 syncJobsOnStartup 验证 scheduler.checkExists 命中.
     */
    @Test
    void scheduleJob_writesToScheduler() throws SchedulerException {
        // given - 准备一个 ACTIVE 任务
        SysJobConf job = makeJobConf("JOB_IT_001", "IT_SYNC_JOB", "0 0 3 * * ?", "FIRE_ONCE_NOW");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(job));

        // when - 触发同步（@PostConstruct 时 mock 默认返回空 list，本调用是显式的二次同步）
        jobService.syncJobsOnStartup();

        // then - 真实 Scheduler 命中 JobKey
        JobKey jobKey = JobKey.jobKey("IT_SYNC_JOB", "DEFAULT");
        assertThat(scheduler.checkExists(jobKey)).isTrue();
        TriggerKey triggerKey = TriggerKey.triggerKey("IT_SYNC_JOB_TRIGGER", "DEFAULT");
        assertThat(scheduler.checkExists(triggerKey)).isTrue();
        // 清理：本测试 RAMJobStore 但 Spring context 复用，需移除避免污染后续 test
        scheduler.deleteJob(jobKey);
    }

    /**
     * 场景 2：模拟应用重启时 cron 变更场景—— deleteJob + 重新 sync 后 CronTrigger 反映新 cron.
     *
     * <p>说明：Quartz 运行期 {@code scheduler.scheduleJob(detail, trigger)} 在 jobKey 已存在时抛
     * {@code ObjectAlreadyExistsException}，不会自动覆盖。SchedulerFactoryBean 的
     * {@code overwriteExistingJobs=true} 仅作用于启动期由 SchedulerFactoryBean 持有的
     * jobDetails/triggers 列表，对运行期 scheduleJob 调用不生效。
     *
     * <p>真实 cron 变更工作流是：管理员变更 sys_job_conf.cron_expr → 重启应用 → @PostConstruct
     * 在 RAMJobStore 内（一份新生 Scheduler 状态）按新 cron 重新 schedule。本测试用 deleteJob 模拟
     * "重启清空 RAMJobStore 后再 sync"，验证 cron 变更生效路径正确。
     */
    @Test
    void rescheduleJob_updatesCron() throws SchedulerException {
        // given - 先用旧 cron 注册
        SysJobConf jobV1 = makeJobConf("JOB_IT_002", "IT_RESCHEDULE_JOB", "0 0 1 * * ?", "FIRE_ONCE_NOW");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(jobV1));
        jobService.syncJobsOnStartup();

        TriggerKey triggerKey = TriggerKey.triggerKey("IT_RESCHEDULE_JOB_TRIGGER", "DEFAULT");
        CronTrigger triggerV1 = (CronTrigger) scheduler.getTrigger(triggerKey);
        assertThat(triggerV1).isNotNull();
        assertThat(triggerV1.getCronExpression()).isEqualTo("0 0 1 * * ?");

        // when - 模拟应用重启：清空 Scheduler 中该 job → 再用新 cron 调用 syncJobsOnStartup
        scheduler.deleteJob(JobKey.jobKey("IT_RESCHEDULE_JOB", "DEFAULT"));
        SysJobConf jobV2 = makeJobConf("JOB_IT_002", "IT_RESCHEDULE_JOB", "0 0 5 * * ?", "FIRE_ONCE_NOW");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(jobV2));
        jobService.syncJobsOnStartup();

        // then - 真实 Scheduler 中 cron 已更新
        CronTrigger triggerV2 = (CronTrigger) scheduler.getTrigger(triggerKey);
        assertThat(triggerV2).isNotNull();
        assertThat(triggerV2.getCronExpression()).isEqualTo("0 0 5 * * ?");
        // 清理
        scheduler.deleteJob(JobKey.jobKey("IT_RESCHEDULE_JOB", "DEFAULT"));
    }

    /**
     * 场景 3：triggerJob 通过真实 Scheduler 触发 → JobExecutionLogger 监听到执行 → mapper.insert 被调用.
     *
     * <p>验证端到端：JobService.triggerJob → 真实 Scheduler → 异步执行 → JobListener 写日志.
     */
    @Test
    void triggerJobNow_invokesQuartzTrigger() throws Exception {
        // given - 同步一个 ACTIVE 任务（DAO 校验由 mock JobConfMapper.selectById/selectByJobKey 提供）
        SysJobConf job = makeJobConf("JOB_IT_003", "IT_TRIGGER_JOB", "0 0 23 * * ?", "FIRE_ONCE_NOW");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(job));
        when(jobConfMapper.selectById("JOB_IT_003")).thenReturn(job);
        // JobExecutionLogger.jobToBeExecuted 内部调用 selectByJobKey 取 jobConf
        when(jobConfMapper.selectByJobKey("IT_TRIGGER_JOB")).thenReturn(job);
        jobService.syncJobsOnStartup();

        // when - 通过 JobService.triggerJob 走真实 Scheduler 路径
        jobService.triggerJob("JOB_IT_003", "集成测试触发", "2026-06-03", "emp_it_001");

        // then - 真实 Scheduler 异步执行，JobExecutionLogger.jobToBeExecuted 被调用 → runLogMapper.insert 被调用
        // 给真实 Scheduler 异步线程一点时间执行（最多等 5 秒）
        long deadline = System.currentTimeMillis() + 5_000L;
        boolean inserted = false;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(jobRunLogMapper, atLeastOnce()).insert((SysJobRunLog) org.mockito.ArgumentMatchers.any());
                inserted = true;
                break;
            } catch (AssertionError ignored) {
                // 还没触发到 listener，继续等
                Thread.sleep(50);
            }
        }
        assertThat(inserted).as("Scheduler 应在 5 秒内异步触发 IT_TRIGGER_JOB → JobExecutionLogger.insert").isTrue();
        // 验证 mapper.updateLastRunTime 被调用（jobWasExecuted 钩子也已触发）
        verify(jobConfMapper, atLeastOnce()).updateLastRunTime(anyString(), org.mockito.ArgumentMatchers.any());
        // 清理
        scheduler.deleteJob(JobKey.jobKey("IT_TRIGGER_JOB", "DEFAULT"));
    }

    // ── Helper ─────────────────────────────────────────────────

    private SysJobConf makeJobConf(String id, String jobKey, String cronExpr, String misfirePolicy) {
        SysJobConf conf = new SysJobConf();
        conf.setId(id);
        conf.setJobKey(jobKey);
        conf.setJobName(jobKey + "_NAME");
        conf.setCronExpr(cronExpr);
        conf.setMisfirePolicy(misfirePolicy);
        conf.setStatus("ACTIVE");
        conf.setAllowManualTrigger(1);
        // 反射加载使用 governance 测试范围内的最简 Quartz Job NoOp 实现
        conf.setQuartzJobClass(SyncTestNoOpJob.class.getName());
        conf.setCreatedTime(LocalDateTime.now());
        conf.setUpdatedTime(LocalDateTime.now());
        return conf;
    }

    /**
     * 测试专用 SpringBoot 启动类（与 P1.6 的 QuartzConfigIntegrationITApp 同模式）.
     *
     * <p>装配链：
     * <ul>
     *   <li>scanBasePackages 用不存在的子包关闭 component scan，避免拉入 governance 全模块的
     *       WebMvcAuthConfig / GovCacheConfig / MinioConfig 等无关 bean</li>
     *   <li>@Import 显式装入 3 个目标类：QuartzConfig（其内部 @Bean 提供 AutowiringSpringBeanJobFactory）
     *       + JobExecutionLogger + JobService</li>
     *   <li>JobConfMapper / JobRunLogMapper 由测试 @MockBean 提供</li>
     * </ul>
     */
    @SpringBootApplication(scanBasePackages = "com.bank.branch.platform.governance.service.itscope.notexist")
    @Import({QuartzConfig.class, JobExecutionLogger.class, JobService.class})
    static class JobServiceITApp {
    }
}
