package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PerfCalcApiImpl.triggerMetricCalc 单元测试（Task P3.3 Red）.
 *
 * <p>目标：将原 UOE 占位替换为委托 {@link MetricCalcService#calcMetric} 的实现。
 *
 * <p>覆盖：
 * <ul>
 *   <li>triggerMetricCalc(metricCode, dataDate, version) 委托 MetricCalcService.calcMetric 并返回 taskId</li>
 *   <li>传入 null metricCode / null dataDate / null version 时抛 IllegalArgumentException</li>
 *   <li>不影响其他未替换的 UOE 占位（triggerKpiCalc / triggerRecalc 仍抛 UOE）</li>
 * </ul>
 */
class PerfCalcApiImplTriggerTest extends PerformanceServiceTestBase {

    @Mock
    private PerfRunTaskService perfRunTaskService;

    @Mock
    private MetricCalcService metricCalcService;

    @InjectMocks
    private PerfCalcApiImpl perfCalcApi;

    @Test
    @DisplayName("triggerMetricCalc：委托 MetricCalcService.calcMetric 返回 taskId")
    void triggerMetricCalc_delegatesToMetricCalcService() {
        when(metricCalcService.calcMetric(
                eq("M_EMP_TEST"), eq(LocalDate.of(2026, 4, 22)), eq("v20260422")))
                .thenReturn("TASK_FROM_SVC_0001");

        String taskId = perfCalcApi.triggerMetricCalc(
                "M_EMP_TEST", LocalDate.of(2026, 4, 22), "v20260422");

        assertThat(taskId).isEqualTo("TASK_FROM_SVC_0001");
        verify(metricCalcService).calcMetric(
                "M_EMP_TEST", LocalDate.of(2026, 4, 22), "v20260422");
        verifyNoInteractions(perfRunTaskService);
    }

    @Test
    @DisplayName("triggerMetricCalc：null metricCode 抛 IllegalArgumentException")
    void triggerMetricCalc_nullMetricCode_throws() {
        assertThatThrownBy(() -> perfCalcApi.triggerMetricCalc(
                null, LocalDate.of(2026, 4, 22), "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(metricCalcService);
    }

    @Test
    @DisplayName("triggerMetricCalc：null dataDate 抛 IllegalArgumentException")
    void triggerMetricCalc_nullDataDate_throws() {
        assertThatThrownBy(() -> perfCalcApi.triggerMetricCalc("M", null, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(metricCalcService);
    }

    @Test
    @DisplayName("triggerMetricCalc：null version 抛 IllegalArgumentException")
    void triggerMetricCalc_nullVersion_throws() {
        assertThatThrownBy(() -> perfCalcApi.triggerMetricCalc("M", LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(metricCalcService);
    }

    @Test
    @DisplayName("triggerKpiCalc 仍保持 V1.1 UOE 占位（P4 交付）")
    void triggerKpiCalc_stillUOE() {
        assertThatThrownBy(() -> perfCalcApi.triggerKpiCalc(LocalDate.of(2026, 4, 22)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("triggerRecalc 仍保持 V1.1 UOE 占位（P7 交付）")
    void triggerRecalc_stillUOE() {
        assertThatThrownBy(() -> perfCalcApi.triggerRecalc(
                "MONTHLY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                "reason", "op"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
