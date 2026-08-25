package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

/**
 * JobService.@PostConstruct.syncJobsOnStartup 单元测试（V1.6 quartz 整合 P3.1）.
 *
 * <p>验证启动时遍历 {@code sys_job_conf} 表 ACTIVE 任务并按 quartz_job_class + cron_expr +
 * misfire_policy 注册 JobDetail + CronTrigger 到 Scheduler。
 *
 * <p>关键场景：
 * <ul>
 *   <li>{@link #sync_3JobsActive_schedulesAll}: 3 个 ACTIVE 任务全部成功调度</li>
 *   <li>{@link #sync_oneJobConfigError_continuesOthers}: 1 个 quartz_job_class 反射失败时，
 *       其他 2 个仍正常调度，整体不抛异常（容错隔离）</li>
 *   <li>{@link #sync_zeroJobs_doesNothing}: 空列表场景下 Scheduler 0 次调用</li>
 * </ul>
 *
 * <p>测试中 quartz_job_class 使用 governance 模块测试范围内的最简 Quartz Job 类
 * {@link SyncTestNoOpJob}（位于本测试文件的同包），避免跨模块依赖 performance-engine-center.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceSyncOnStartupTest {

    @Mock
    JobConfMapper jobConfMapper;

    @Mock
    JobRunLogMapper jobRunLogMapper;

    @Mock
    Scheduler scheduler;

    @InjectMocks
    JobService jobService;

    /**
     * Mockito 的 {@link InjectMocks} 在 {@code @RequiredArgsConstructor} 生成的 2 参构造器
     * （JobConfMapper / JobRunLogMapper）注入完成后，对剩余字段（Scheduler 是 {@code @Autowired(required=false)}
     * 非 final）的 field 注入策略偶发不命中，因此显式通过 {@link ReflectionTestUtils#setField} 注入.
     */
    @BeforeEach
    void injectScheduler() {
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);
    }

    /**
     * 场景 1: 3 个 ACTIVE 任务 → scheduler.scheduleJob 调用 3 次（全部成功）.
     */
    @Test
    void sync_3JobsActive_schedulesAll() throws Exception {
        SysJobConf job1 = makeJobConf("JOB_001", "DAILY_KPI_CALC", "0 30 0 * * ?", "FIRE_ONCE_NOW");
        SysJobConf job2 = makeJobConf("JOB_002", "SYS_CONTROL_CLEANUP", "0 0 2 * * ?", "DO_NOTHING");
        SysJobConf job3 = makeJobConf("JOB_003", "PERF_RUN_TASK_CLEANUP", "0 0 3 * * ?", "IGNORE_MISFIRE_POLICY");
        // 注入测试 Job 字节码全限定名（governance 测试范围内最简 Quartz Job）
        String testJobClass = SyncTestNoOpJob.class.getName();
        job1.setQuartzJobClass(testJobClass);
        job2.setQuartzJobClass(testJobClass);
        job3.setQuartzJobClass(testJobClass);

        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(job1, job2, job3));

        jobService.syncJobsOnStartup();

        verify(scheduler, times(3)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    /**
     * 场景 2: 3 个任务中 1 个 quartz_job_class 不存在（ClassNotFoundException）→
     * 其他 2 个仍正常调度，整体方法不抛异常.
     *
     * <p>验证容错语义：单条同步失败不影响其他（log.error + 计数 + 继续）.
     */
    @Test
    void sync_oneJobConfigError_continuesOthers() throws Exception {
        SysJobConf jobOk1 = makeJobConf("JOB_001", "DAILY_KPI_CALC", "0 30 0 * * ?", "FIRE_ONCE_NOW");
        SysJobConf jobBad = makeJobConf("JOB_BAD", "BAD_JOB", "0 0 1 * * ?", "FIRE_ONCE_NOW");
        SysJobConf jobOk2 = makeJobConf("JOB_002", "SYS_CONTROL_CLEANUP", "0 0 2 * * ?", "DO_NOTHING");
        String validClass = SyncTestNoOpJob.class.getName();
        jobOk1.setQuartzJobClass(validClass);
        jobBad.setQuartzJobClass("non.existent.NonExistentJobClass");
        jobOk2.setQuartzJobClass(validClass);

        when(jobConfMapper.selectByStatus("ACTIVE"))
                .thenReturn(List.of(jobOk1, jobBad, jobOk2));

        // 不应抛异常
        jobService.syncJobsOnStartup();

        // 仅 ok1 + ok2 调度（jobBad 在反射时抛 ClassNotFoundException）
        verify(scheduler, times(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    /**
     * 场景 3: 空列表（无 ACTIVE 任务）→ Scheduler 0 次调用.
     */
    @Test
    void sync_zeroJobs_doesNothing() throws Exception {
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of());

        jobService.syncJobsOnStartup();

        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void sync_activeJobWithoutCron_addsDurableJobDetailWithoutTrigger() throws Exception {
        SysJobConf job = makeJobConf("JOB_COORD", "LEVEL1_METRIC_CALC", null, "FIRE_ONCE_NOW");
        job.setQuartzJobClass(SyncTestNoOpJob.class.getName());
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(job));

        jobService.syncJobsOnStartup();

        verify(scheduler).addJob(any(JobDetail.class), eq(true));
        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    // ── Helper ─────────────────────────────────────────────────

    private SysJobConf makeJobConf(String id, String jobKey, String cronExpr, String misfirePolicy) {
        SysJobConf conf = new SysJobConf();
        conf.setId(id);
        conf.setJobKey(jobKey);
        conf.setJobName(jobName(jobKey));
        conf.setCronExpr(cronExpr);
        conf.setMisfirePolicy(misfirePolicy);
        conf.setStatus("ACTIVE");
        conf.setAllowManualTrigger(1);
        conf.setCreatedTime(LocalDateTime.now());
        conf.setUpdatedTime(LocalDateTime.now());
        return conf;
    }

    private String jobName(String key) {
        return key + "_NAME";
    }
}
