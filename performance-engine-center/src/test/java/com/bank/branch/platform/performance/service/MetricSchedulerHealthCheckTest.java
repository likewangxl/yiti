package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MetricSchedulerHealthCheck 单元测试（V1.7 P6 Task 19）.
 */
class MetricSchedulerHealthCheckTest {

    private MetricSchedulerService schedulerService;
    private MetricDefService metricDefService;
    private JobApi jobApi;
    private MetricSchedulerHealthCheck check;

    @BeforeEach
    void setup() {
        schedulerService = mock(MetricSchedulerService.class);
        metricDefService = mock(MetricDefService.class);
        jobApi = mock(JobApi.class);
        check = new MetricSchedulerHealthCheck(schedulerService, metricDefService, jobApi);
    }

    @Test
    void check_re_registers_metrics_missing_from_sys_job_conf() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_LOST");
        when(metricDefService.listSchedulable()).thenReturn(List.of(def));
        when(jobApi.getJobConf("PERF_METRIC_M_LOST")).thenReturn(Optional.empty());

        check.runCheck();

        verify(schedulerService).register(def);
    }

    @Test
    void check_skips_metrics_already_registered() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_OK");
        when(metricDefService.listSchedulable()).thenReturn(List.of(def));
        when(jobApi.getJobConf("PERF_METRIC_M_OK")).thenReturn(Optional.of(new JobConfDTO()));

        check.runCheck();

        verify(schedulerService, never()).register(any());
    }
}
