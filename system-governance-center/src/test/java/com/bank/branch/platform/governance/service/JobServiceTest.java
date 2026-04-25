package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobRunLogDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.entity.SysJobRunLog;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 任务调度服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    JobConfMapper jobConfMapper;
    @Mock
    JobRunLogMapper jobRunLogMapper;
    @InjectMocks
    JobService jobService;

    // ── getJobConf ──────────────────────────────────────────────

    /**
     * 测试获取任务配置 - 任务存在时返回 DTO
     */
    @Test
    void getJobConf_found_returnsDTO() {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectByJobKey("PERF_DAILY_CALC")).thenReturn(conf);

        JobConfDTO dto = jobService.getJobConf("PERF_DAILY_CALC");

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("JOB_001");
        assertThat(dto.getJobKey()).isEqualTo("PERF_DAILY_CALC");
        assertThat(dto.getJobName()).isEqualTo("绩效日计算");
    }

    /**
     * 测试获取任务配置 - 任务不存在时抛出 GOV-40004
     */
    @Test
    void getJobConf_notFound_throwsGov40004() {
        when(jobConfMapper.selectByJobKey("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.getJobConf("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40004"));
    }

    // ── startJobRun ─────────────────────────────────────────────

    /**
     * 测试任务启动 - 成功时创建 RUNNING 状态日志并返回日志ID
     */
    @Test
    void startJobRun_success_createsRunningLog() {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        when(jobRunLogMapper.existsRunningByJobId("JOB_001")).thenReturn(false);
        when(jobRunLogMapper.insert(any(SysJobRunLog.class))).thenReturn(1);

        String runLogId = jobService.startJobRun("JOB_001", "SCHEDULED", "SYSTEM");

        assertThat(runLogId).isNotBlank();
        // 验证插入了一条 RUNNING 状态的日志
        verify(jobRunLogMapper).insert(argThat(log ->
                "RUNNING".equals(log.getStatus())
                        && "SCHEDULED".equals(log.getTriggerType())
                        && "SYSTEM".equals(log.getCreatedBy())
                        && log.getStartTime() != null
        ));
        // 验证更新了任务的最后执行时间
        verify(jobConfMapper).updateById(argThat(c -> c.getLastRunTime() != null));
    }

    /**
     * 测试任务启动 - 任务不存在时抛出 GOV-40004
     */
    @Test
    void startJobRun_jobNotFound_throwsGov40004() {
        when(jobConfMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.startJobRun("NOT_EXIST", "SCHEDULED", "SYSTEM"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40004"));
    }

    /**
     * 测试任务启动 - 已有 RUNNING 日志时抛出 GOV-40903（并发防控）
     */
    @Test
    void startJobRun_alreadyRunning_throwsGov40903() {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        when(jobRunLogMapper.existsRunningByJobId("JOB_001")).thenReturn(true);

        assertThatThrownBy(() -> jobService.startJobRun("JOB_001", "SCHEDULED", "SYSTEM"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40903"));
    }

    // ── completeJobRun ──────────────────────────────────────────

    /**
     * 测试完成任务执行 - 更新状态为 SUCCESS 且设置 endTime
     */
    @Test
    void completeJobRun_updatesStatus() {
        SysJobRunLog log = makeRunLog("LOG_001", "JOB_001", "RUNNING");
        when(jobRunLogMapper.selectById("LOG_001")).thenReturn(log);
        when(jobRunLogMapper.updateById(any())).thenReturn(1);

        jobService.completeJobRun("LOG_001");

        verify(jobRunLogMapper).updateById(argThat(l ->
                "SUCCESS".equals(l.getStatus()) && l.getEndTime() != null
        ));
    }

    /**
     * 测试完成任务执行 - 日志不存在时抛出 GOV-40007
     */
    @Test
    void completeJobRun_notFound_throwsGov40007() {
        when(jobRunLogMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.completeJobRun("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40007"));
    }

    // ── failJobRun ──────────────────────────────────────────────

    /**
     * 测试任务执行失败 - 记录 FAILED 状态和错误信息
     */
    @Test
    void failJobRun_recordsError() {
        SysJobRunLog log = makeRunLog("LOG_001", "JOB_001", "RUNNING");
        when(jobRunLogMapper.selectById("LOG_001")).thenReturn(log);
        when(jobRunLogMapper.updateById(any())).thenReturn(1);

        jobService.failJobRun("LOG_001", "NullPointerException at line 42");

        verify(jobRunLogMapper).updateById(argThat(l ->
                "FAILED".equals(l.getStatus())
                        && l.getEndTime() != null
                        && "NullPointerException at line 42".equals(l.getErrorMsg())
        ));
    }

    // ── pauseJob / resumeJob (P3.3: scheduler 联动) ───────────────

    /**
     * P3.3 关键测试：暂停任务时同时调用 mapper.updateStatus(jobId, "PAUSED")
     * 与 scheduler.pauseJob(JobKey) —— 数据库与 Quartz 一致。
     */
    @Test
    void pauseJob_existingJob_callsScheduler() throws Exception {
        // given
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        Scheduler scheduler = mock(Scheduler.class);
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);

        // when
        jobService.pauseJob("JOB_001");

        // then - mapper.updateStatus 被调用，参数为 ("JOB_001", "PAUSED")
        verify(jobConfMapper).updateStatus("JOB_001", "PAUSED");
        // scheduler.pauseJob 被调用，JobKey name=jobKey, group=DEFAULT
        ArgumentCaptor<JobKey> keyCap = ArgumentCaptor.forClass(JobKey.class);
        verify(scheduler).pauseJob(keyCap.capture());
        assertThat(keyCap.getValue().getName()).isEqualTo("PERF_DAILY_CALC");
        assertThat(keyCap.getValue().getGroup()).isEqualTo("DEFAULT");
    }

    /**
     * P3.3: 暂停任务时任务不存在抛 GOV-40004（复用 TASK_NOT_FOUND）.
     */
    @Test
    void pauseJob_jobNotFound_throwsException() {
        when(jobConfMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.pauseJob("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40004"));
    }

    /**
     * P3.3: scheduler.pauseJob 抛 SchedulerException 时显式抛 JOB_PAUSE_FAILED (GOV-50005).
     */
    @Test
    void pauseJob_schedulerThrows_throwsPauseFailed() throws Exception {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        Scheduler scheduler = mock(Scheduler.class);
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);
        doThrow(new SchedulerException("quartz internal error"))
                .when(scheduler).pauseJob(any(JobKey.class));

        assertThatThrownBy(() -> jobService.pauseJob("JOB_001"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-50005"));
    }

    /**
     * P3.3 关键测试：恢复任务时同时调用 mapper.updateStatus(jobId, "ACTIVE")
     * 与 scheduler.resumeJob(JobKey).
     */
    @Test
    void resumeJob_existingJob_callsScheduler() throws Exception {
        // given
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        Scheduler scheduler = mock(Scheduler.class);
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);

        // when
        jobService.resumeJob("JOB_001");

        // then
        verify(jobConfMapper).updateStatus("JOB_001", "ACTIVE");
        ArgumentCaptor<JobKey> keyCap = ArgumentCaptor.forClass(JobKey.class);
        verify(scheduler).resumeJob(keyCap.capture());
        assertThat(keyCap.getValue().getName()).isEqualTo("PERF_DAILY_CALC");
        assertThat(keyCap.getValue().getGroup()).isEqualTo("DEFAULT");
    }

    /**
     * P3.3: 恢复任务时任务不存在抛 GOV-40004.
     */
    @Test
    void resumeJob_jobNotFound_throwsException() {
        when(jobConfMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.resumeJob("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40004"));
    }

    /**
     * P3.3: scheduler.resumeJob 抛 SchedulerException 时显式抛 JOB_RESUME_FAILED (GOV-50006).
     */
    @Test
    void resumeJob_schedulerThrows_throwsResumeFailed() throws Exception {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        Scheduler scheduler = mock(Scheduler.class);
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);
        doThrow(new SchedulerException("quartz internal error"))
                .when(scheduler).resumeJob(any(JobKey.class));

        assertThatThrownBy(() -> jobService.resumeJob("JOB_001"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-50006"));
    }

    // ── listJobs ────────────────────────────────────────────────

    /**
     * 测试分页查询任务列表
     */
    @Test
    void listJobs_returnsPageResult() {
        List<SysJobConf> records = List.of(makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算"));
        when(jobConfMapper.countByPage("绩效")).thenReturn(1L);
        when(jobConfMapper.selectByPage(eq("绩效"), eq(0), eq(20))).thenReturn(records);

        PageResult<JobConfDTO> page = jobService.listJobs("绩效", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getJobKey()).isEqualTo("PERF_DAILY_CALC");
    }

    // ── listRunLogs ─────────────────────────────────────────────

    /**
     * 测试分页查询任务执行日志
     */
    @Test
    void listRunLogs_returnsPageResult() {
        List<SysJobRunLog> logs = List.of(makeRunLog("LOG_001", "JOB_001", "SUCCESS"));
        when(jobRunLogMapper.countByJobId("JOB_001")).thenReturn(1L);
        when(jobRunLogMapper.selectByJobId(eq("JOB_001"), eq(0), eq(20))).thenReturn(logs);

        PageResult<JobRunLogDTO> page = jobService.listRunLogs("JOB_001", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getJobId()).isEqualTo("JOB_001");
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 测试 failJobRun：日志不存在时抛出 GOV-40007
     */
    @Test
    void failJobRun_notFound_throwsGov40007() {
        when(jobRunLogMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.failJobRun("NOT_EXIST", "error"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40007"));
    }

    /**
     * 测试 listJobs：keyword 为 null 时正常返回
     */
    @Test
    void listJobs_nullKeyword_returnsAll() {
        when(jobConfMapper.countByPage(null)).thenReturn(0L);
        when(jobConfMapper.selectByPage(isNull(), eq(0), eq(20))).thenReturn(List.of());

        PageResult<JobConfDTO> page = jobService.listJobs(null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(0L);
        assertThat(page.getRecords()).isEmpty();
    }

    // ── triggerJob (P3.2: scheduler 联动) ────────────────────────

    /**
     * P3.2 关键测试：手动触发任务时调用 scheduler.triggerJob(JobKey, JobDataMap),
     * dataMap 含 triggerType=MANUAL + operatorEmpId + triggerReason.
     */
    @Test
    void triggerJob_existingJob_callsScheduler() throws Exception {
        // given
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        Scheduler scheduler = mock(Scheduler.class);
        ReflectionTestUtils.setField(jobService, "scheduler", scheduler);

        // when
        JobTriggerRespDTO resp = jobService.triggerJob("JOB_001", "手动测试", "emp001");

        // then - 验证 scheduler.triggerJob(JobKey, JobDataMap) 被调用
        ArgumentCaptor<JobKey> keyCap = ArgumentCaptor.forClass(JobKey.class);
        ArgumentCaptor<JobDataMap> dataCap = ArgumentCaptor.forClass(JobDataMap.class);
        verify(scheduler).triggerJob(keyCap.capture(), dataCap.capture());
        // JobKey 来自 sys_job_conf.job_key，组采用 "DEFAULT"（与 P3.1 syncJobsOnStartup 一致）
        assertThat(keyCap.getValue().getName()).isEqualTo("PERF_DAILY_CALC");
        assertThat(keyCap.getValue().getGroup()).isEqualTo("DEFAULT");
        // dataMap 字段与 JobExecutionLogger.jobToBeExecuted 读取的 key 一致
        JobDataMap data = dataCap.getValue();
        assertThat(data.getString("triggerType")).isEqualTo("MANUAL");
        assertThat(data.getString("operatorEmpId")).isEqualTo("emp001");
        assertThat(data.getString("triggerReason")).isEqualTo("手动测试");

        // 响应 DTO 含 jobId / triggerType=MANUAL / triggerTime
        assertThat(resp).isNotNull();
        assertThat(resp.getJobId()).isEqualTo("JOB_001");
        assertThat(resp.getTriggerType()).isEqualTo("MANUAL");
        assertThat(resp.getTriggerTime()).isNotBlank();
    }

    /**
     * P3.2: 任务不存在时抛 GOV-40004（复用 TASK_NOT_FOUND）.
     */
    @Test
    void triggerJob_jobNotFound_throwsGov40004() {
        when(jobConfMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> jobService.triggerJob("NOT_EXIST", "原因", "emp001"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40004"));
    }

    /**
     * P3.2: scheduler 未启用（null）时必须显式抛 BizException（区别于 P3.1 启动同步可静默跳过）.
     * 用户主动触发场景下，scheduler 不可用必须明确报错。
     */
    @Test
    void triggerJob_schedulerNull_throwsTriggerFailed() {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);
        // 不注入 scheduler，保持 null

        assertThatThrownBy(() -> jobService.triggerJob("JOB_001", "原因", "emp001"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-50004"));
    }

    /**
     * P3.3 修复 P3.2 错误码语义错配：allow_manual_trigger != 1 时
     * 必须抛 JOB_MANUAL_NOT_ALLOWED (GOV-40302) 而非 TASK_ALREADY_RUNNING (GOV-40903).
     *
     * <p>P3.2 实现误用 GOV-40903（"任务正在执行中"），与抛出消息 "该任务不允许手动触发" 严重错配；
     * P3.3 引入 JOB_MANUAL_NOT_ALLOWED (GOV-40302) 作为 403 Forbidden 语义，本测试验证修复。</p>
     */
    @Test
    void triggerJob_allowManualFalse_throwsManualNotAllowed() {
        SysJobConf conf = makeJobConf("JOB_001", "PERF_DAILY_CALC", "绩效日计算");
        // 强制 allowManualTrigger=0：不允许手动触发
        conf.setAllowManualTrigger(0);
        when(jobConfMapper.selectById("JOB_001")).thenReturn(conf);

        assertThatThrownBy(() -> jobService.triggerJob("JOB_001", "原因", "emp001"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40302"));
    }

    // ── Helper Methods ──────────────────────────────────────────

    private SysJobConf makeJobConf(String id, String jobKey, String jobName) {
        SysJobConf conf = new SysJobConf();
        conf.setId(id);
        conf.setJobKey(jobKey);
        conf.setJobName(jobName);
        conf.setCronExpr("0 2 * * *");
        conf.setStatus("ACTIVE");
        conf.setAllowManualTrigger(1);
        conf.setCreatedTime(LocalDateTime.now());
        conf.setUpdatedTime(LocalDateTime.now());
        return conf;
    }

    private SysJobRunLog makeRunLog(String id, String jobId, String status) {
        SysJobRunLog log = new SysJobRunLog();
        log.setId(id);
        log.setJobId(jobId);
        log.setTriggerType("SCHEDULED");
        log.setStatus(status);
        log.setStartTime(LocalDateTime.now());
        log.setCreatedBy("SYSTEM");
        log.setCreatedTime(LocalDateTime.now());
        return log;
    }
}
