package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * KpiApiImpl V1.1 方法实现单元测试（Task P4.3 Red）.
 *
 * <p>替换 3 个 UOE 占位：
 * <ul>
 *   <li>getCurrentKpiTotal(empId, cycleType) → 读 kpi_result 最新一条（asOfDate DESC）</li>
 *   <li>getCurrentKpiResult(empId, cycleType) → 读最新一条并组装 DTO</li>
 *   <li>getKpiHistory(empId, cycleType, from, to) → 时间范围内全部 DTO 列表</li>
 * </ul>
 */
class KpiApiImplV11Test extends PerformanceServiceTestBase {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private KpiResultMapper kpiResultMapper;

    @InjectMocks
    private KpiApiImpl kpiApi;

    @Test
    @DisplayName("getCurrentKpiTotal：返回最新一条 total_score")
    void getCurrentKpiTotal_returnsLatestScore() {
        KpiResult r = buildResult(100L, "E001", "MONTHLY",
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                new BigDecimal("88.5"));
        when(kpiResultMapper.selectLatestByEmpCycle(eq("E001"), eq("MONTHLY")))
                .thenReturn(r);

        BigDecimal total = kpiApi.getCurrentKpiTotal("E001", "MONTHLY");

        assertThat(total).isEqualByComparingTo("88.5");
    }

    @Test
    @DisplayName("getCurrentKpiTotal：无历史记录返回 null")
    void getCurrentKpiTotal_noRecord_returnsNull() {
        when(kpiResultMapper.selectLatestByEmpCycle(eq("E999"), eq("MONTHLY")))
                .thenReturn(null);

        assertThat(kpiApi.getCurrentKpiTotal("E999", "MONTHLY")).isNull();
    }

    @Test
    @DisplayName("getCurrentKpiResult：组装最新一条 DTO")
    void getCurrentKpiResult_returnsDto() {
        KpiResult r = buildResult(10L, "E001", "MONTHLY",
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                new BigDecimal("92.0000"));
        r.setDetailJson("{\"items\":[]}");
        r.setDataVersion("v20260401");

        when(kpiResultMapper.selectLatestByEmpCycle(eq("E001"), eq("MONTHLY")))
                .thenReturn(r);

        Optional<KpiResultDTO> dto = kpiApi.getCurrentKpiResult("E001", "MONTHLY");

        assertThat(dto).isPresent();
        KpiResultDTO value = dto.get();
        assertThat(value.getEmpId()).isEqualTo("E001");
        assertThat(value.getCycleType()).isEqualTo("MONTHLY");
        assertThat(value.getCycleDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(value.getAsOfDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(value.getDataVersion()).isEqualTo("v20260401");
        assertThat(value.getKpiTotalScore()).isEqualByComparingTo("92");
        assertThat(value.getDetailJson()).isEqualTo("{\"items\":[]}");
    }

    @Test
    @DisplayName("getCurrentKpiResult：无记录返回 Optional.empty")
    void getCurrentKpiResult_empty() {
        when(kpiResultMapper.selectLatestByEmpCycle(any(), any())).thenReturn(null);

        assertThat(kpiApi.getCurrentKpiResult("E999", "MONTHLY")).isEmpty();
    }

    @Test
    @DisplayName("getKpiHistory：时间范围内记录全部返回 DTO 列表")
    void getKpiHistory_returnsList() {
        KpiResult r1 = buildResult(1L, "E001", "MONTHLY",
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1),
                new BigDecimal("70"));
        KpiResult r2 = buildResult(2L, "E001", "MONTHLY",
                LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1),
                new BigDecimal("80"));
        when(kpiResultMapper.selectByEmpCycleRange(
                eq("E001"), eq("MONTHLY"),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 3, 31))))
                .thenReturn(List.of(r2, r1));

        List<KpiResultDTO> list = kpiApi.getKpiHistory(
                "E001", "MONTHLY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(list).hasSize(2);
        assertThat(list).extracting(KpiResultDTO::getId).containsExactly(2L, 1L);
        assertThat(list).extracting(KpiResultDTO::getKpiTotalScore)
                .containsExactly(new BigDecimal("80"), new BigDecimal("70"));
    }

    @Test
    @DisplayName("getKpiHistory：范围内无记录返回空列表")
    void getKpiHistory_empty() {
        when(kpiResultMapper.selectByEmpCycleRange(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<KpiResultDTO> list = kpiApi.getKpiHistory(
                "E_NO", "MONTHLY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(list).isEmpty();
    }

    private KpiResult buildResult(Long id, String empId, String cycleType,
                                   LocalDate cycleDate, LocalDate asOfDate,
                                   BigDecimal totalScore) {
        KpiResult r = new KpiResult();
        r.setId(id);
        r.setEmpId(empId);
        r.setCycleType(cycleType);
        r.setCycleDate(cycleDate);
        r.setAsOfDate(asOfDate);
        r.setKpiTotalScore(totalScore);
        r.setCalculatedTime(LocalDateTime.now());
        return r;
    }
}
