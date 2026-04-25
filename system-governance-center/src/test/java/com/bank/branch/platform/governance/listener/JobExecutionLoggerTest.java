package com.bank.branch.platform.governance.listener;

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
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JobExecutionLogger 单元测试（V1.6 quartz 整合 P1.5）.
 * <p>
 * 覆盖：getName / SCHEDULED 触发 / MANUAL 触发 / SUCCESS 完成 / FAILED 错误截断 / 异常隔离。
 */
@ExtendWith(MockitoExtension.class)
class JobExecutionLoggerTest {

    @Mock
    private JobConfMapper jobConfMapper;

    @Mock
    private JobRunLogMapper runLogMapper;

    @Mock
    private JobExecutionContext context;

    @Mock
    private JobDetail jobDetail;

    @InjectMocks
    private JobExecutionLogger listener;

    @Test
    void getName_returnsClassName() {
        assertThat(listener.getName()).isEqualTo("JobExecutionLogger");
    }

    @Test
    void jobToBeExecuted_scheduledTrigger_writesRunningLog() {
        SysJobConf jobConf = new SysJobConf();
        jobConf.setId("JOB_DAILY_KPI_CALC");
        jobConf.setJobKey("DAILY_KPI_CALC");

        JobDataMap mergedData = new JobDataMap(); // 不含 triggerType → 默认 SCHEDULED
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));
        when(context.getMergedJobDataMap()).thenReturn(mergedData);
        when(context.getScheduledFireTime()).thenReturn(new Date());
        when(jobConfMapper.selectByJobKey("DAILY_KPI_CALC")).thenReturn(jobConf);

        listener.jobToBeExecuted(context);

        ArgumentCaptor<SysJobRunLog> captor = ArgumentCaptor.forClass(SysJobRunLog.class);
        verify(runLogMapper).insert(captor.capture());
        SysJobRunLog inserted = captor.getValue();
        assertThat(inserted.getJobId()).isEqualTo("JOB_DAILY_KPI_CALC");
        assertThat(inserted.getTriggerType()).isEqualTo("SCHEDULED");
        assertThat(inserted.getStatus()).isEqualTo("RUNNING");
        assertThat(inserted.getId()).isNotBlank();
        verify(context).put(eq("runLogId"), eq(inserted.getId()));
    }

    @Test
    void jobToBeExecuted_manualTrigger_writesManualLog() {
        SysJobConf jobConf = new SysJobConf();
        jobConf.setId("JOB_DAILY_KPI_CALC");
        jobConf.setJobKey("DAILY_KPI_CALC");

        JobDataMap mergedData = new JobDataMap();
        mergedData.put("triggerType", "MANUAL");
        mergedData.put("operatorEmpId", "EMP_001");

        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));
        when(context.getMergedJobDataMap()).thenReturn(mergedData);
        when(context.getScheduledFireTime()).thenReturn(new Date());
        when(jobConfMapper.selectByJobKey("DAILY_KPI_CALC")).thenReturn(jobConf);

        listener.jobToBeExecuted(context);

        ArgumentCaptor<SysJobRunLog> captor = ArgumentCaptor.forClass(SysJobRunLog.class);
        verify(runLogMapper).insert(captor.capture());
        SysJobRunLog inserted = captor.getValue();
        assertThat(inserted.getTriggerType()).isEqualTo("MANUAL");
        assertThat(inserted.getCreatedBy()).isEqualTo("EMP_001");
    }

    @Test
    void jobWasExecuted_success_writesSuccessLog() {
        when(context.get("runLogId")).thenReturn("RUNLOG_123");
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));

        listener.jobWasExecuted(context, null);

        verify(runLogMapper).updateSuccess(eq("RUNLOG_123"), any());
        verify(jobConfMapper).updateLastRunTime(eq("DAILY_KPI_CALC"), any());
    }

    @Test
    void jobWasExecuted_failure_writesFailedLogWithTruncatedErrorMsg() {
        StringBuilder bigStack = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            bigStack.append("x");
        }
        JobExecutionException jobEx = new JobExecutionException(new RuntimeException(bigStack.toString()));

        when(context.get("runLogId")).thenReturn("RUNLOG_123");
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));

        listener.jobWasExecuted(context, jobEx);

        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(runLogMapper).updateFailed(eq("RUNLOG_123"), any(), errorMsgCaptor.capture());
        assertThat(errorMsgCaptor.getValue().length()).isLessThanOrEqualTo(4000);
    }

    @Test
    void jobToBeExecuted_mapperThrows_doesNotPropagateToQuartz() {
        // selectByJobKey 抛异常，merged data map 不会被读取（实现里 selectByJobKey 在前），
        // 故仅 stub 必要的 getJobDetail / jobKey（catch 块 log 也会读取 jobDetail.getKey）。
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("UNKNOWN", "DEFAULT"));
        when(jobConfMapper.selectByJobKey("UNKNOWN")).thenThrow(new RuntimeException("DB down"));

        // 不应抛出
        listener.jobToBeExecuted(context);
        // 验证 runLogMapper.insert 未被调用（因为前置 selectByJobKey 已挂掉）
        verifyNoInteractions(runLogMapper);
    }
}
