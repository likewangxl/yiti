package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JobService scheduler allowed-job-keys contract tests.
 *
 * <p>The allowlist is opt-in: an empty set keeps the existing scheduling behavior.
 * A non-empty set must fence every path that can put a job into Quartz.</p>
 */
class JobServiceSchedulerAllowlistTest {

    private JobConfMapper jobConfMapper;
    private JobRunLogMapper jobRunLogMapper;
    private Scheduler scheduler;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobConfMapper = mock(JobConfMapper.class);
        jobRunLogMapper = mock(JobRunLogMapper.class);
        scheduler = mock(Scheduler.class);
        jobService = new JobService(jobConfMapper, jobRunLogMapper);
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);
        setAllowedJobKeys();
    }

    @Test
    void startupSync_restrictiveAllowlist_schedulesOnlyAllowedJob() throws Exception {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf allowed = job("J_ALLOWED", "BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(allowed, blocked));

        jobService.syncJobsOnStartup();

        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        verify(scheduler, never()).addJob(any(JobDetail.class), eq(true));
    }

    @Test
    void startupSync_emptyAllowlist_preservesExistingBehavior() throws Exception {
        setAllowedJobKeys();
        SysJobConf first = job("J_FIRST", "RED_ENGINE_TASK_WINDOW");
        SysJobConf second = job("J_SECOND", "BRANCH_DASHBOARD_BATCH");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(first, second));

        jobService.syncJobsOnStartup();

        verify(scheduler, org.mockito.Mockito.times(2))
                .scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void registerJob_blocked_rejectsBeforeDbOrQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        RegisterJobCmd cmd = registerCommand("RED_ENGINE_TASK_WINDOW");

        assertBlocked(() -> jobService.registerJob(cmd), "RED_ENGINE_TASK_WINDOW");

        verifyNoInteractions(jobConfMapper, scheduler);
    }

    @Test
    void triggerJobByKey_blocked_rejectsBeforeQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        when(jobConfMapper.selectByJobKey("RED_ENGINE_TASK_WINDOW")).thenReturn(blocked);

        assertBlocked(() -> jobService.triggerJobByKey(
                "RED_ENGINE_TASK_WINDOW", "AUTO", "test", null, null, "SYSTEM"),
                "RED_ENGINE_TASK_WINDOW");

        verify(jobConfMapper).selectByJobKey("RED_ENGINE_TASK_WINDOW");
        verifyNoInteractions(scheduler, jobRunLogMapper);
    }

    @Test
    void triggerJobById_blocked_rejectsBeforeQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        when(jobConfMapper.selectById("J_BLOCKED")).thenReturn(blocked);

        assertBlocked(() -> jobService.triggerJob(
                "J_BLOCKED", "test", null, null, "EMP001"),
                "RED_ENGINE_TASK_WINDOW");

        verify(jobConfMapper).selectById("J_BLOCKED");
        verifyNoInteractions(scheduler, jobRunLogMapper);
    }

    @Test
    void resumeJob_blocked_rejectsBeforeStatusWriteOrQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        when(jobConfMapper.selectById("J_BLOCKED")).thenReturn(blocked);

        assertBlocked(() -> jobService.resumeJob("J_BLOCKED"), "RED_ENGINE_TASK_WINDOW");

        verify(jobConfMapper).selectById("J_BLOCKED");
        verify(jobConfMapper, never()).updateStatus(anyString(), anyString());
        verifyNoInteractions(scheduler);
    }

    @Test
    void pauseJob_blocked_rejectsBeforeStatusWriteOrQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        when(jobConfMapper.selectById("J_BLOCKED")).thenReturn(blocked);

        assertBlocked(() -> jobService.pauseJob("J_BLOCKED"), "RED_ENGINE_TASK_WINDOW");

        verify(jobConfMapper).selectById("J_BLOCKED");
        verify(jobConfMapper, never()).updateStatus(anyString(), anyString());
        verifyNoInteractions(scheduler);
    }

    @Test
    void unregisterJob_blocked_rejectsBeforeDbOrQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");

        assertBlocked(() -> jobService.unregisterJob("RED_ENGINE_TASK_WINDOW"),
                "RED_ENGINE_TASK_WINDOW");

        verifyNoInteractions(jobConfMapper, scheduler);
    }

    @Test
    void scheduleQuartzJobWithData_blocked_rejectsBeforeReflectionOrQuartz() {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        blocked.setQuartzJobClass("com.example.DoesNotExist");

        assertBlocked(() -> ReflectionTestUtils.invokeMethod(
                jobService, "scheduleQuartzJobWithData", blocked, Map.of("k", "v")),
                "RED_ENGINE_TASK_WINDOW");

        verifyNoInteractions(scheduler);
    }

    @Test
    void registerJob_allowed_writesAndSchedules() throws Exception {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        RegisterJobCmd cmd = registerCommand("BRANCH_DASHBOARD_BATCH");
        when(jobConfMapper.selectByJobKey("BRANCH_DASHBOARD_BATCH")).thenReturn(null);

        String id = jobService.registerJob(cmd);

        assertThat(id).isNotBlank();
        verify(jobConfMapper).insert(any(SysJobConf.class));
        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void triggerJobByKey_allowed_triggersQuartz() throws Exception {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf allowed = job("J_ALLOWED", "BRANCH_DASHBOARD_BATCH");
        when(jobConfMapper.selectByJobKey("BRANCH_DASHBOARD_BATCH")).thenReturn(allowed);
        doNothing().when(scheduler).triggerJob(any(JobKey.class), any());

        var response = jobService.triggerJobByKey(
                "BRANCH_DASHBOARD_BATCH", "AUTO", "test", "2026-09-11", null, "SYSTEM");

        assertThat(response.getJobKey()).isEqualTo("BRANCH_DASHBOARD_BATCH");
        verify(scheduler).triggerJob(any(JobKey.class), any());
    }

    @Test
    void resumeJob_allowed_updatesStatusAndResumesQuartz() throws Exception {
        setAllowedJobKeys("BRANCH_DASHBOARD_BATCH");
        SysJobConf allowed = job("J_ALLOWED", "BRANCH_DASHBOARD_BATCH");
        when(jobConfMapper.selectById("J_ALLOWED")).thenReturn(allowed);

        jobService.resumeJob("J_ALLOWED");

        verify(jobConfMapper).updateStatus("J_ALLOWED", "ACTIVE");
        verify(scheduler).resumeJob(any(JobKey.class));
    }

    private void setAllowedJobKeys(String... keys) {
        ReflectionTestUtils.setField(jobService, "allowedJobKeys", new LinkedHashSet<>(Set.of(keys)));
    }

    private SysJobConf job(String id, String key) {
        SysJobConf conf = new SysJobConf();
        conf.setId(id);
        conf.setJobKey(key);
        conf.setJobName(key);
        conf.setCronExpr("0 0 2 * * ?");
        conf.setQuartzJobClass(SyncTestNoOpJob.class.getName());
        conf.setMisfirePolicy("FIRE_ONCE_NOW");
        conf.setStatus("ACTIVE");
        conf.setAllowManualTrigger(1);
        conf.setCreatedTime(LocalDateTime.now());
        conf.setUpdatedTime(LocalDateTime.now());
        return conf;
    }

    private RegisterJobCmd registerCommand(String jobKey) {
        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey(jobKey);
        cmd.setJobName(jobKey);
        cmd.setCronExpr("0 0 2 * * ?");
        cmd.setQuartzJobClass(SyncTestNoOpJob.class.getName());
        cmd.setMisfirePolicy("FIRE_ONCE_NOW");
        cmd.setJobData(Map.of("key", "value"));
        return cmd;
    }

    private void assertBlocked(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String key) {
        assertThatThrownBy(callable)
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getCode()).isEqualTo("GOV-40303");
                    assertThat(exception.getMessage()).contains(key);
                });
    }
}
