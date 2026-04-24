package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.service.HistoryRecalcService;
import com.bank.branch.platform.performance.service.KpiCalcService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private HistoryRecalcService historyRecalcService;

    @Mock
    private KpiCalcService kpiCalcService;

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
    @DisplayName("triggerKpiCalc V1.3 R2.1 委托 KpiCalcService.calcScheme（Red 阶段仍 UOE，Green 后通过）")
    void triggerKpiCalc_delegatesToKpiCalcService() {
        LocalDate cycleDate = LocalDate.of(2026, 4, 30);
        LocalDate asOfDate = LocalDate.of(2026, 5, 1);
        when(kpiCalcService.calcScheme(
                eq("SCHEME_X"), eq("MONTHLY"), eq(cycleDate), eq(asOfDate), eq("v1")))
                .thenReturn(3);

        int count = perfCalcApi.triggerKpiCalc(
                "SCHEME_X", "MONTHLY", cycleDate, asOfDate, "v1");

        assertThat(count).isEqualTo(3);
        verify(kpiCalcService).calcScheme(
                "SCHEME_X", "MONTHLY", cycleDate, asOfDate, "v1");
    }

    @Test
    @DisplayName("triggerRecalc(5 参数) P7.2 已交付：委托 HistoryRecalcService.recalc")
    void triggerRecalc_5args_delegatesToHistoryRecalcService() {
        // V1.3 R4.3：PerfCalcApiImpl 改调 7 参数 recalc（cycleType 透传）
        when(historyRecalcService.recalc(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 3, 31)),
                any(), anyString(), eq("reason"), eq("op"), eq("MONTHLY")))
                .thenReturn("P7_TASK");

        String taskId = perfCalcApi.triggerRecalc(
                "MONTHLY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                "reason", "op");

        assertThat(taskId).isEqualTo("P7_TASK");
        verify(historyRecalcService).recalc(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 3, 31)),
                any(), anyString(), eq("reason"), eq("op"), eq("MONTHLY"));
    }
}
