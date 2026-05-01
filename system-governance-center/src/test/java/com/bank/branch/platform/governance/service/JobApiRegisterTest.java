package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.Scheduler;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JobService.registerJob 单元测试（V1.7 TDD Red 阶段）.
 */
class JobApiRegisterTest {

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
    void registerJob_invalid_cron_throws_GOV_50010() {
        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey("PERF_METRIC_TEST");
        cmd.setCronExpr("invalid cron");
        cmd.setQuartzJobClass("com.bank.branch.platform.performance.job.quartz.MetricExecuteQuartzJob");
        assertThatThrownBy(() -> service.registerJob(cmd))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("GOV-50010"));
    }

    @Test
    void registerJob_class_not_found_throws_GOV_50011() {
        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey("PERF_METRIC_TEST");
        cmd.setCronExpr("0 0 2 * * ?");
        cmd.setQuartzJobClass("com.bank.platform.NotExist");
        assertThatThrownBy(() -> service.registerJob(cmd))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("GOV-50011"));
    }

    @Test
    void registerJob_inserts_when_not_exist() throws Exception {
        when(jobConfMapper.selectByJobKey("PERF_METRIC_X")).thenReturn(null);
        RegisterJobCmd cmd = sample("PERF_METRIC_X");

        String id = service.registerJob(cmd);

        assertThat(id).isNotBlank();
        ArgumentCaptor<SysJobConf> cap = ArgumentCaptor.forClass(SysJobConf.class);
        verify(jobConfMapper).insert(cap.capture());
        assertThat(cap.getValue().getJobKey()).isEqualTo("PERF_METRIC_X");
        assertThat(cap.getValue().getCronExpr()).isEqualTo("0 0 2 * * ?");
    }

    @Test
    void registerJob_updates_when_exists_overwrite() throws Exception {
        SysJobConf existing = new SysJobConf();
        existing.setId("OLD_ID");
        existing.setJobKey("PERF_METRIC_X");
        existing.setCronExpr("OLD_CRON");
        when(jobConfMapper.selectByJobKey("PERF_METRIC_X")).thenReturn(existing);

        RegisterJobCmd cmd = sample("PERF_METRIC_X");
        cmd.setCronExpr("0 30 3 * * ?");

        String id = service.registerJob(cmd);

        assertThat(id).isEqualTo("OLD_ID");
        verify(jobConfMapper, never()).insert(any(SysJobConf.class));
        verify(jobConfMapper).updateById(any(SysJobConf.class));
    }

    @Test
    void registerJob_skips_scheduler_when_null() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "scheduler", null);
        when(jobConfMapper.selectByJobKey(any())).thenReturn(null);
        RegisterJobCmd cmd = sample("PERF_METRIC_X");
        String id = service.registerJob(cmd);
        assertThat(id).isNotBlank();
    }

    private RegisterJobCmd sample(String jobKey) {
        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey(jobKey);
        cmd.setJobName("test");
        cmd.setCronExpr("0 0 2 * * ?");
        cmd.setQuartzJobClass(org.quartz.Job.class.getName());
        cmd.setMisfirePolicy("FIRE_ONCE_NOW");
        cmd.setJobData(Map.of("metricCode", "M_TEST"));
        return cmd;
    }
}
