package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JobService.unregisterJob 单元测试（V1.7）.
 *
 * <p>覆盖：正常注销、幂等（不存在）、scheduler 异常隔离、scheduler null 三种场景.
 */
class JobApiUnregisterTest {

    private JobConfMapper jobConfMapper;
    private JobRunLogMapper jobRunLogMapper;
    private Scheduler scheduler;
    private JobService service;

    @BeforeEach
    void setup() {
        jobConfMapper = mock(JobConfMapper.class);
        jobRunLogMapper = mock(JobRunLogMapper.class);
        scheduler = mock(Scheduler.class);
        service = new JobService(jobConfMapper, jobRunLogMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scheduler", scheduler);
    }

    @Test
    void unregister_calls_scheduler_deleteJob_then_mapper_delete() throws Exception {
        service.unregisterJob("PERF_METRIC_X");
        verify(scheduler).deleteJob(JobKey.jobKey("PERF_METRIC_X", "DEFAULT"));
        verify(scheduler).deleteJob(JobKey.jobKey("PERF_METRIC_X", "PERF_METRIC"));
        verify(jobConfMapper).deleteByJobKey("PERF_METRIC_X");
    }

    @Test
    void unregister_idempotent_when_jobKey_not_exists() throws Exception {
        when(jobConfMapper.deleteByJobKey("NOT_EXIST")).thenReturn(0);
        service.unregisterJob("NOT_EXIST");
        verify(jobConfMapper).deleteByJobKey("NOT_EXIST");
    }

    @Test
    void unregister_continues_when_scheduler_throws() throws Exception {
        doThrow(new SchedulerException("boom"))
            .when(scheduler).deleteJob(any(JobKey.class));
        service.unregisterJob("PERF_METRIC_X");
        verify(jobConfMapper).deleteByJobKey("PERF_METRIC_X");
    }

    @Test
    void unregister_skips_scheduler_when_null() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scheduler", null);
        service.unregisterJob("PERF_METRIC_X");
        verify(jobConfMapper).deleteByJobKey("PERF_METRIC_X");
    }
}
