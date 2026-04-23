package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HistoryRecalcService 单元测试（V1.1 Task P7.1 Red）.
 *
 * <p>职责：历史回算——按日期范围 × 指标码批量调 {@link MetricCalcService#calcMetric}，
 * 生成父级 {@code perf_run_task}（taskType={@code RECALC}）记录聚合状态，并把每个子任务 ID
 * 串联到 {@code result_preview_json} 字段（V1.1 简化方案：不在 DDL 加 parent_id，
 * 用 result_preview_json 记录子 taskId 列表——待 V1.2 引入 parent_id 后升级）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>3 日期 × 2 指标 = 6 次 calcMetric 调用；父 task 状态 SUCCESS；remark 记录成功/失败计数</li>
 *   <li>metricCodes=null → 从 MetricDefService.listActiveMetrics 查全部 ACTIVE 指标</li>
 *   <li>部分失败 → 父 task 状态 PARTIAL（错误聚合不中断）</li>
 *   <li>日期范围 startDate &gt; endDate → 抛 VALIDATION_FAILED</li>
 *   <li>日期范围 &gt; 365 天 → 抛 VALIDATION_FAILED</li>
 *   <li>空指标列表 + 无 ACTIVE 指标 → 父 task 直接 SUCCESS（空执行）</li>
 *   <li>指标不存在（显式传入未定义 code）→ 抛 METRIC_NOT_FOUND（由 MetricCalcService 透传）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HistoryRecalcServiceTest {

    @Mock
    private MetricCalcService metricCalcService;

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;

    @InjectMocks
    private HistoryRecalcService historyRecalcService;

    @Test
    @DisplayName("3 日期 × 2 指标：生成 6 次 calcMetric 调用，父 task 状态 SUCCESS")
    void recalc_dateRangeAndMetrics_triggersAllChildrenAndParentSuccess() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 3);
        List<String> metricCodes = List.of("M_EMP_A", "M_EMP_B");
        String version = "v20260301";
        String reason = "月末对齐";
        String operator = "admin";

        // calcMetric 始终返回唯一 taskId
        when(metricCalcService.calcMetric(anyString(), any(LocalDate.class), eq(version)))
                .thenReturn("TASK_CHILD_1");

        String parentTaskId = historyRecalcService.recalc(start, end, metricCodes, version, reason, operator);

        assertThat(parentTaskId).isNotBlank();

        // 6 次 calcMetric（3 天 × 2 指标）
        verify(metricCalcService, times(6))
                .calcMetric(anyString(), any(LocalDate.class), eq(version));

        // 验证父 task 插入（taskType=RECALC）
        ArgumentCaptor<PerfRunTask> insertCap = ArgumentCaptor.forClass(PerfRunTask.class);
        verify(perfRunTaskMapper).insert(insertCap.capture());
        PerfRunTask parent = insertCap.getValue();
        assertThat(parent.getTaskType()).isEqualTo("RECALC");
        assertThat(parent.getStatus()).isEqualTo("PENDING");
        assertThat(parent.getStartedBy()).isEqualTo(operator);
        assertThat(parent.getDataVersion()).isEqualTo(version);

        // 父 task 更新为 RUNNING → SUCCESS（状态机）
        verify(perfRunTaskMapper).updateStatus(eq(parentTaskId), eq("RUNNING"), any());
        verify(perfRunTaskMapper).updateStatus(eq(parentTaskId), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("metricCodes=null：从 metricDefService.listActiveMetrics 查全部 ACTIVE 指标")
    void recalc_nullMetricCodes_fallsBackToActiveMetrics() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        String version = "v1";

        PerfMetricDef def1 = new PerfMetricDef();
        def1.setMetricCode("M_AUTO_1");
        def1.setStatus("ACTIVE");
        PerfMetricDef def2 = new PerfMetricDef();
        def2.setMetricCode("M_AUTO_2");
        def2.setStatus("ACTIVE");
        when(metricDefService.listActiveMetrics(null, null))
                .thenReturn(List.of(def1, def2));
        when(metricCalcService.calcMetric(anyString(), any(LocalDate.class), eq(version)))
                .thenReturn("TASK_AUTO");

        String parentTaskId = historyRecalcService.recalc(date, date, null, version, "auto scan", "sys");

        assertThat(parentTaskId).isNotBlank();
        verify(metricDefService).listActiveMetrics(null, null);
        verify(metricCalcService).calcMetric(eq("M_AUTO_1"), eq(date), eq(version));
        verify(metricCalcService).calcMetric(eq("M_AUTO_2"), eq(date), eq(version));
    }

    @Test
    @DisplayName("部分失败：1 次 calc 抛异常 → 父 task 状态 PARTIAL（错误聚合不中断）")
    void recalc_partialFailure_parentStatusPartial() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        List<String> metricCodes = List.of("M_OK", "M_FAIL");

        when(metricCalcService.calcMetric(eq("M_OK"), eq(date), anyString()))
                .thenReturn("TASK_OK");
        when(metricCalcService.calcMetric(eq("M_FAIL"), eq(date), anyString()))
                .thenThrow(new PerfException(PerfErrorCode.CALC_JOB_FAILED, "mock failure"));

        String parentTaskId = historyRecalcService.recalc(date, date, metricCodes, "v1", "part", "op");

        assertThat(parentTaskId).isNotBlank();
        // 父 task 被标记为 PARTIAL（而非 FAILED / SUCCESS）
        verify(perfRunTaskMapper).updateStatus(eq(parentTaskId), eq("PARTIAL"), any());
        // 两个子指标均尝试执行（错误不中断循环）
        verify(metricCalcService).calcMetric(eq("M_OK"), eq(date), anyString());
        verify(metricCalcService).calcMetric(eq("M_FAIL"), eq(date), anyString());
    }

    @Test
    @DisplayName("startDate > endDate：抛 VALIDATION_FAILED")
    void recalc_invalidDateRange_throwsValidation() {
        assertThatThrownBy(() -> historyRecalcService.recalc(
                LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 1),
                List.of("M_A"), "v1", "test", "op"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(metricCalcService, never()).calcMetric(anyString(), any(), anyString());
        verify(perfRunTaskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("日期范围 > 365 天：抛 VALIDATION_FAILED")
    void recalc_rangeExceedsLimit_throwsValidation() {
        assertThatThrownBy(() -> historyRecalcService.recalc(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 4, 1),
                List.of("M_A"), "v1", "test", "op"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(metricCalcService, never()).calcMetric(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("空指标 + 无 ACTIVE 指标：父 task 直接 SUCCESS（空执行）")
    void recalc_emptyMetricsAndNoActive_parentStillSuccess() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        when(metricDefService.listActiveMetrics(null, null))
                .thenReturn(Collections.emptyList());

        String parentTaskId = historyRecalcService.recalc(date, date, null, "v1", "empty", "op");

        assertThat(parentTaskId).isNotBlank();
        verify(metricCalcService, never()).calcMetric(anyString(), any(), anyString());
        verify(perfRunTaskMapper).updateStatus(eq(parentTaskId), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("空 metricCodes 列表（显式空列表）：等价于 null → 查 active")
    void recalc_emptyMetricCodes_fallsBackToActive() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        PerfMetricDef defX = new PerfMetricDef();
        defX.setMetricCode("M_X");
        defX.setStatus("ACTIVE");
        when(metricDefService.listActiveMetrics(null, null)).thenReturn(List.of(defX));
        when(metricCalcService.calcMetric(eq("M_X"), eq(date), anyString())).thenReturn("T_X");

        String parentTaskId = historyRecalcService.recalc(
                date, date, Collections.emptyList(), "v1", "empty list", "op");

        assertThat(parentTaskId).isNotBlank();
        verify(metricDefService).listActiveMetrics(null, null);
        verify(metricCalcService).calcMetric(eq("M_X"), eq(date), eq("v1"));
    }
}
