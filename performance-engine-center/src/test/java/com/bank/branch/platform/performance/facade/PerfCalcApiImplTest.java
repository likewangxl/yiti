package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.service.HistoryRecalcService;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.PerfRunTaskService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.RunTaskTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PerfCalcApiImpl 单元测试.
 *
 * <p>覆盖:
 * <ul>
 *   <li>getRunTask (V1.0 实现): 存在 / 不存在 / 全字段装配 3 场景</li>
 *   <li>triggerRecalc (V1.1 P7.2 实现): 5/7 参数签名分别委托 HistoryRecalcService</li>
 *   <li>triggerKpiCalc (V1.1 P4 交付前): 仍抛 UOE（其实 P4 已交付，但本 Test 保留兼容
 *       —— 真实 P4 委托由 KpiApi / DailyKpiCalcJob 负责）</li>
 * </ul>
 *
 * <p>纯 Mock 测试, 不启动 Spring 容器.
 *
 * <p>测试数据前缀 {@code TEST_RT_} (由 {@link RunTaskTestDataBuilder} 统一约定)。
 */
class PerfCalcApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private PerfRunTaskService perfRunTaskService;

    @Mock
    private MetricCalcService metricCalcService;

    @Mock
    private HistoryRecalcService historyRecalcService;

    @InjectMocks
    private PerfCalcApiImpl perfCalcApi;

    // ------------------------- V1.2 契约: triggerKpiCalc 仍 UOE -------------------------

    @Test
    @DisplayName("triggerKpiCalc: V1.1 阶段仍抛 UOE（V1.1 KPI 计算已在 KpiApi 交付，本方法作为契约冗余保留至 V1.2）")
    void triggerKpiCalc_throwsUOE() {
        assertThatThrownBy(() -> perfCalcApi.triggerKpiCalc(LocalDate.of(2026, 4, 20)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("V1.2 delivered");
        verifyNoInteractions(perfRunTaskService);
    }

    // ------------------------- V1.1 契约: triggerRecalc (P7.2 交付) -------------------------

    @Test
    @DisplayName("triggerRecalc(5 参数): 委托 HistoryRecalcService.recalc 并返回父 taskId")
    void triggerRecalc_5args_delegatesToHistoryRecalcService() {
        when(historyRecalcService.recalc(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 3, 31)),
                any(), anyString(), eq("补录 Q1 数据"), eq("E001")))
                .thenReturn("PARENT_TASK_001");

        String taskId = perfCalcApi.triggerRecalc(
                "MONTHLY",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                "补录 Q1 数据",
                "E001");

        assertThat(taskId).isEqualTo("PARENT_TASK_001");
        verify(historyRecalcService).recalc(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 3, 31)),
                any(), anyString(), eq("补录 Q1 数据"), eq("E001"));
    }

    @Test
    @DisplayName("triggerRecalc(7 参数): 全字段透传 HistoryRecalcService.recalc")
    void triggerRecalc_7args_delegatesToHistoryRecalcService() {
        List<String> metricCodes = List.of("M_EMP_A", "M_EMP_B");
        when(historyRecalcService.recalc(
                eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 3, 31)),
                eq(metricCodes),
                eq("v20260301"),
                eq("补录 3 月数据"),
                eq("admin")))
                .thenReturn("PARENT_TASK_002");

        String taskId = perfCalcApi.triggerRecalc(
                "MONTHLY",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                metricCodes,
                "v20260301",
                "补录 3 月数据",
                "admin");

        assertThat(taskId).isEqualTo("PARENT_TASK_002");
        verify(historyRecalcService).recalc(
                eq(LocalDate.of(2026, 3, 1)),
                eq(LocalDate.of(2026, 3, 31)),
                eq(metricCodes),
                eq("v20260301"),
                eq("补录 3 月数据"),
                eq("admin"));
    }

    // ------------------------- V1.0 实现: getRunTask -------------------------

    @Test
    @DisplayName("getRunTask: 存在时返回 Optional 包装的 DTO")
    void getRunTask_whenExists_returnsOptional() {
        PerfRunTask task = RunTaskTestDataBuilder.task("FACADE_OK", "E001");
        String id = task.getId();
        when(perfRunTaskService.getById(id)).thenReturn(Optional.of(task));

        Optional<PerfRunTaskDTO> dto = perfCalcApi.getRunTask(id);

        assertThat(dto).isPresent();
        assertThat(dto.get().getId()).isEqualTo(id);
        assertThat(dto.get().getTaskType()).isEqualTo("METRIC_RUN");
        assertThat(dto.get().getStatus()).isEqualTo("RUNNING");
        assertThat(dto.get().getStartedBy()).isEqualTo("E001");
    }

    @Test
    @DisplayName("getRunTask: 不存在时返回 Optional.empty")
    void getRunTask_whenNotExists_returnsEmpty() {
        when(perfRunTaskService.getById("NO_SUCH")).thenReturn(Optional.empty());

        assertThat(perfCalcApi.getRunTask("NO_SUCH")).isEmpty();
    }

    @Test
    @DisplayName("getRunTask: 装配器完整映射 Entity 全部对外字段到 DTO")
    void getRunTask_assemblesAllFields() {
        PerfRunTask task = RunTaskTestDataBuilder.task(
                "FACADE_FULL", "KPI_RUN",
                LocalDate.of(2026, 4, 20), "SUCCESS", "E002");
        task.setEndTime(task.getStartTime().plusMinutes(5));
        task.setErrorMsg("no-error");
        task.setResultPreviewJson("{\"ok\":true}");
        String id = task.getId();
        when(perfRunTaskService.getById(id)).thenReturn(Optional.of(task));

        Optional<PerfRunTaskDTO> dto = perfCalcApi.getRunTask(id);

        assertThat(dto).isPresent();
        PerfRunTaskDTO value = dto.get();
        // 主键 + 业务字段
        assertThat(value.getId()).isEqualTo(id);
        assertThat(value.getTaskType()).isEqualTo("KPI_RUN");
        // DDL 字段 task_key 直接映射为 DTO 同名字段 taskKey
        assertThat(value.getTaskKey()).isEqualTo("TEST_RT_FACADE_FULL");
        assertThat(value.getDataDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(value.getDataVersion()).isEqualTo("V_TEST_FACADE_FULL");
        assertThat(value.getStatus()).isEqualTo("SUCCESS");
        assertThat(value.getStartedBy()).isEqualTo("E002");
        assertThat(value.getStartTime()).isEqualTo(task.getStartTime());
        assertThat(value.getEndTime()).isEqualTo(task.getEndTime());
        assertThat(value.getErrorMsg()).isEqualTo("no-error");
        assertThat(value.getResultPreviewJson()).isEqualTo("{\"ok\":true}");
    }
}
