package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.SysControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MetricExecuteQuartzJob 单元测试（V1.7 P5）.
 *
 * <p>TDD Red→Green：3 case 覆盖正常委托、metricCode 缺失抛异常、业务异常包装三个场景。
 */
class MetricExecuteQuartzJobTest {

    private MetricCalcService metricCalcService;
    private SysControlService sysControlService;
    private MetricExecuteQuartzJob job;
    private JobExecutionContext context;

    @BeforeEach
    void setup() {
        metricCalcService = mock(MetricCalcService.class);
        sysControlService = mock(SysControlService.class);
        job = new MetricExecuteQuartzJob();
        ReflectionTestUtils.setField(job, "metricCalcService", metricCalcService);
        ReflectionTestUtils.setField(job, "sysControlService", sysControlService);
        context = mock(JobExecutionContext.class);
        JobDetail detail = mock(JobDetail.class);
        when(context.getJobDetail()).thenReturn(detail);
        when(detail.getKey()).thenReturn(JobKey.jobKey("PERF_METRIC_M_A", "PERF_METRIC"));
        when(sysControlService.getActiveVersionOrFallback(any(LocalDate.class))).thenReturn("v1");
    }

    @Test
    @DisplayName("execute: 从 JobDataMap 取 metricCode，以 SCHEDULED 触发类型调用 calcMetric")
    void execute_passes_metricCode_and_SCHEDULED_to_calcMetric() throws Exception {
        // Given
        JobDataMap data = new JobDataMap();
        data.put("metricCode", "M_A");
        when(context.getMergedJobDataMap()).thenReturn(data);

        // When
        job.execute(context);

        // Then: calcMetric 被调用一次，triggerType = "SCHEDULED"
        verify(metricCalcService).calcMetric(eq("M_A"), any(LocalDate.class), eq("v1"), eq("SCHEDULED"));
    }

    @Test
    @DisplayName("execute: metricCode 缺失时抛 JobExecutionException 并包含 'metricCode' 描述")
    void execute_throws_when_metricCode_missing() {
        // Given: JobDataMap 中无 metricCode
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());

        // When / Then
        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("metricCode");
    }

    @Test
    @DisplayName("execute: calcMetric 抛业务异常时包装为 JobExecutionException")
    void execute_wraps_business_exception_to_jobExecutionException() {
        // Given
        JobDataMap data = new JobDataMap();
        data.put("metricCode", "M_A");
        when(context.getMergedJobDataMap()).thenReturn(data);
        doThrow(new RuntimeException("boom"))
                .when(metricCalcService).calcMetric(anyString(), any(LocalDate.class), anyString(), anyString());

        // When / Then
        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(JobExecutionException.class);
    }
}
