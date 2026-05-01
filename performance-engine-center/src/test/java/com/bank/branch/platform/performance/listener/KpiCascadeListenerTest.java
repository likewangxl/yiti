package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.event.MetricCalcCompletedEvent;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.service.KpiCalcService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
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
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private KpiCascadeListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        kpiItemMapper = mock(PerfKpiItemMapper.class);
        kpiSchemeService = mock(KpiSchemeService.class);
        kpiCalcService = mock(KpiCalcService.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        listener = new KpiCascadeListener(kpiItemMapper, kpiSchemeService, kpiCalcService, redisTemplate);
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

    /** Redis SETNX 失败时（返回 false）跳过本次计算，不重复触发. */
    @Test
    void redis_lock_failure_skips_calc() {
        when(kpiItemMapper.selectActiveSchemeIdsByMetric("M_A")).thenReturn(List.of("S1"));
        when(kpiSchemeService.getById("S1")).thenReturn(activeScheme("S1", "MONTHLY"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

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
