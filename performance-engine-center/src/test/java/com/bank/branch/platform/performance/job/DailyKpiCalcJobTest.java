package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.service.KpiCalcService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DailyKpiCalcJob 单元测试（Task P4.4 Red）.
 *
 * <p>职责：每日 01:30（默认 cron）遍历所有 ACTIVE 的 KPI 方案，
 * 对每个方案调 {@link KpiCalcService#calcScheme(String, String, LocalDate, LocalDate, String)}.
 *
 * <p>Red 阶段：尚未创建 {@link DailyKpiCalcJob}，本测试编译失败即为 Red.
 */
@ExtendWith(MockitoExtension.class)
class DailyKpiCalcJobTest {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiCalcService kpiCalcService;

    @InjectMocks
    private DailyKpiCalcJob job;

    @Test
    @DisplayName("run：为每个 ACTIVE 方案调 calcScheme")
    void run_callsCalcSchemeForEachActive() {
        PerfKpiScheme s1 = scheme("S1", "TEST_KPI_S1", "MONTHLY");
        PerfKpiScheme s2 = scheme("S2", "TEST_KPI_S2", "MONTHLY");
        PerfKpiScheme s3 = scheme("S3", "TEST_KPI_S3", "QUARTERLY");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1, s2, s3));

        job.run();

        verify(kpiCalcService, times(1)).calcScheme(
                eq("TEST_KPI_S1"), eq("MONTHLY"), any(LocalDate.class), any(LocalDate.class), anyString());
        verify(kpiCalcService, times(1)).calcScheme(
                eq("TEST_KPI_S2"), eq("MONTHLY"), any(LocalDate.class), any(LocalDate.class), anyString());
        verify(kpiCalcService, times(1)).calcScheme(
                eq("TEST_KPI_S3"), eq("QUARTERLY"), any(LocalDate.class), any(LocalDate.class), anyString());
    }

    @Test
    @DisplayName("run：无 ACTIVE 方案时不触发计算")
    void run_noActive_noInteraction() {
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of());

        job.run();

        verifyNoInteractions(kpiCalcService);
    }

    @Test
    @DisplayName("run：单个方案计算失败不影响其它方案")
    void run_oneSchemeFails_othersContinue() {
        PerfKpiScheme s1 = scheme("S1", "TEST_S1", "MONTHLY");
        PerfKpiScheme s2 = scheme("S2", "TEST_S2", "MONTHLY");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1, s2));
        when(kpiCalcService.calcScheme(eq("TEST_S1"), anyString(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        job.run();

        verify(kpiCalcService).calcScheme(eq("TEST_S1"), anyString(), any(), any(), anyString());
        // 即使 S1 抛异常，S2 依然会被调用
        verify(kpiCalcService).calcScheme(eq("TEST_S2"), anyString(), any(), any(), anyString());
    }

    private PerfKpiScheme scheme(String id, String code, String cycleType) {
        PerfKpiScheme s = new PerfKpiScheme();
        s.setId(id);
        s.setSchemeCode(code);
        s.setCycleType(cycleType);
        s.setStatus("ACTIVE");
        return s;
    }
}
