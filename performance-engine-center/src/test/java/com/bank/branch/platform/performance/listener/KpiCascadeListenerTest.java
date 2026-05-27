package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.event.MetricCalcCompletedEvent;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.service.KpiCalcService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.common.web.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KpiCascadeListener 单元测试（V1.7）.
 */
class KpiCascadeListenerTest {

    private PerfKpiItemMapper kpiItemMapper;
    private KpiSchemeService kpiSchemeService;
    private KpiCalcService kpiCalcService;
    private LockManager lockManager;
    private KpiCascadeListener listener;

    @BeforeEach
    void setup() {
        kpiItemMapper = mock(PerfKpiItemMapper.class);
        kpiSchemeService = mock(KpiSchemeService.class);
        kpiCalcService = mock(KpiCalcService.class);
        lockManager = mock(LockManager.class);
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        listener = new KpiCascadeListener(kpiItemMapper, kpiSchemeService, kpiCalcService, lockManager);
    }

    /** FAILED 状态事件不触发 KPI 查询. */
    @Test
    void failed_event_does_not_trigger_kpi() {
        listener.onMetricCompleted(failedEvent("M_A"));
        verifyNoInteractions(kpiItemMapper);
    }

    /** 无依赖方案时不触发 calcScheme. */
    @Test
    void no_dependent_schemes_no_kpi_calc() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of());
        listener.onMetricCompleted(successEvent("M_A"));
        verify(kpiCalcService, never()).calcScheme(any(), any(), any(), any(), any());
    }

    /** MONTHLY 方案将 dataDate 推导为当月第一天作为 cycleDate. */
    @Test
    void monthly_scheme_resolves_cycleDate_to_first_of_month() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "MONTHLY"));

        MetricCalcCompletedEvent ev = new MetricCalcCompletedEvent(
            "M_A", "EMP", LocalDate.of(2026, 4, 15), "v1",
            "SUCCESS", 100, 100, 0, "RT1", "SCHEDULED", LocalDateTime.now());
        listener.onMetricCompleted(ev);

        verify(kpiCalcService).calcScheme(
            eq("CODE_S1"), eq("MONTHLY"),
            eq(LocalDate.of(2026, 4, 1)),
            eq(LocalDate.of(2026, 4, 15)), eq("v1"));
    }

    /** QUARTERLY 方案将 dataDate 推导为所在季度第一天作为 cycleDate. */
    @Test
    void quarterly_scheme_resolves_cycleDate_to_first_of_quarter() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "QUARTERLY"));

        MetricCalcCompletedEvent ev = new MetricCalcCompletedEvent(
            "M_A", "EMP", LocalDate.of(2026, 5, 15), "v1",
            "SUCCESS", 100, 100, 0, "RT1", "SCHEDULED", LocalDateTime.now());
        listener.onMetricCompleted(ev);

        verify(kpiCalcService).calcScheme(
            eq("CODE_S1"), eq("QUARTERLY"),
            eq(LocalDate.of(2026, 4, 1)),   // 二季度第一天
            eq(LocalDate.of(2026, 5, 15)), eq("v1"));
    }

    /** 锁拿不到时（tryLock 返回 false）跳过本次计算，不重复触发. */
    @Test
    void lock_failure_skips_calc() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "MONTHLY"));
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        listener.onMetricCompleted(successEvent("M_A"));
        verify(kpiCalcService, never()).calcScheme(any(), any(), any(), any(), any());
    }

    /** 单方案抛异常时不影响其他方案的计算（异常隔离）. */
    @Test
    void single_scheme_exception_isolated() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1", "S2"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "MONTHLY"));
        when(kpiSchemeService.getById("S2")).thenReturn(activeScheme("S2", "MONTHLY"));
        when(kpiCalcService.calcScheme(eq("CODE_S1"), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        listener.onMetricCompleted(successEvent("M_A"));   // 不抛
        verify(kpiCalcService).calcScheme(eq("CODE_S2"), any(), any(), any(), any());
    }

    /** WEEKLY 方案将 dataDate 推导为当周周一（ISO 8601）作为 cycleDate. */
    @Test
    void weekly_scheme_resolves_cycleDate_to_monday() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "WEEKLY"));

        MetricCalcCompletedEvent ev = new MetricCalcCompletedEvent(
            "M_A", "EMP", LocalDate.of(2026, 4, 15) /*周三*/, "v1",
            "SUCCESS", 100, 100, 0, "RT1", "SCHEDULED", LocalDateTime.now());
        listener.onMetricCompleted(ev);

        verify(kpiCalcService).calcScheme(
            eq("CODE_S1"), eq("WEEKLY"),
            eq(LocalDate.of(2026, 4, 13)),  // 当周周一
            eq(LocalDate.of(2026, 4, 15)), eq("v1"));
    }

    /** YEARLY 方案将 dataDate 推导为当年第一天作为 cycleDate. */
    @Test
    void yearly_scheme_resolves_cycleDate_to_first_day_of_year() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "YEARLY"));

        MetricCalcCompletedEvent ev = new MetricCalcCompletedEvent(
            "M_A", "EMP", LocalDate.of(2026, 7, 4), "v1",
            "SUCCESS", 100, 100, 0, "RT1", "SCHEDULED", LocalDateTime.now());
        listener.onMetricCompleted(ev);

        verify(kpiCalcService).calcScheme(
            eq("CODE_S1"), eq("YEARLY"),
            eq(LocalDate.of(2026, 1, 1)),
            eq(LocalDate.of(2026, 7, 4)), eq("v1"));
    }

    /** 未知 cycleType 触发 PerfException 被外层 catch 隔离，其他方案仍正常计算. */
    @Test
    void unknown_cycleType_throws_then_isolated_by_outer_catch() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1", "S2"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "HOURLY"));
        when(kpiSchemeService.getById("S2")).thenReturn(activeScheme("S2", "MONTHLY"));

        listener.onMetricCompleted(successEvent("M_A"));   // 不抛
        // S1 因 HOURLY 抛 PerfException(KPI_CYCLE_TYPE_INVALID)，被外层 catch 隔离
        verify(kpiCalcService, never()).calcScheme(eq("CODE_S1"), any(), any(), any(), any());
        // S2 仍正常调用
        verify(kpiCalcService).calcScheme(eq("CODE_S2"), any(), any(), any(), any());
    }

    /** getById 抛 KPI_SCHEME_NOT_FOUND 时静默跳过该方案，其他方案仍正常计算. */
    @Test
    void scheme_not_found_silently_skipped() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1", "S2"));
        when(kpiSchemeService.getById("S1"))
            .thenThrow(new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, "S1"));
        when(kpiSchemeService.getById("S2")).thenReturn(activeScheme("S2", "MONTHLY"));

        listener.onMetricCompleted(successEvent("M_A"));   // 不抛
        verify(kpiCalcService, never()).calcScheme(eq("CODE_S1"), any(), any(), any(), any());
        verify(kpiCalcService).calcScheme(eq("CODE_S2"), any(), any(), any(), any());
    }

    /** tryLock 抛异常时退化为不防重，仍正常触发 calcScheme. */
    @Test
    void lock_throws_falls_back_to_no_lock() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "MONTHLY"));
        when(lockManager.tryLock(anyString(), anyString(), anyLong()))
            .thenThrow(new org.springframework.dao.QueryTimeoutException("db down"));

        listener.onMetricCompleted(successEvent("M_A"));
        verify(kpiCalcService).calcScheme(eq("CODE_S1"), any(), any(), any(), any());
    }

    // ---- 辅助方法 ----

    private MetricCalcCompletedEvent successEvent(String code) {
        return new MetricCalcCompletedEvent(code, "EMP", LocalDate.of(2026, 4, 15), "v1",
            "SUCCESS", 100, 100, 0, "RT1", "SCHEDULED", LocalDateTime.now());
    }

    private MetricCalcCompletedEvent failedEvent(String code) {
        return new MetricCalcCompletedEvent(code, "EMP", LocalDate.of(2026, 4, 15), "v1",
            "FAILED", 0, 0, 0, "RT1", "SCHEDULED", LocalDateTime.now());
    }

    private PerfKpiScheme activeScheme(String id, String cycleType) {
        PerfKpiScheme s = new PerfKpiScheme();
        s.setId(id);
        s.setSchemeCode("CODE_" + id);
        s.setStatus("ACTIVE");
        s.setCycleType(cycleType);
        return s;
    }
}
