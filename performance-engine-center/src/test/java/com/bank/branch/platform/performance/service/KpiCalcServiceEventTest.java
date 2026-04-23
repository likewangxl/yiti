package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.event.KpiCalcCompletedEvent;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiCalcService 批量完成后发布 KpiCalcCompletedEvent 集成测试 (V1.2 Q4.1, Red).
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>{@code calcScheme} 成功完成（empCount>0）后调用 {@link PerfEventPublisher#publish}
 *       投递 {@link KpiCalcCompletedEvent}，字段完整</li>
 *   <li>宽表无员工数据（empIds 为空）时不发布事件（与"全失败/empCount=0"区分）</li>
 *   <li>事件字段契约：schemeCode / cycleType / cycleDate / asOfDate / version / empCount
 *       对齐入参 + 实际落地数</li>
 * </ul>
 *
 * <p>采用 Mockito 单元测试：Mock 所有依赖的 Service/Mapper + PerfEventPublisher，
 * 事务后投递机制由 {@link com.bank.branch.platform.performance.event.PerfEventPublisherTest} 覆盖。
 */
class KpiCalcServiceEventTest extends PerformanceServiceTestBase {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private EmpIndexResultMapper empIndexResultMapper;

    @Mock
    private KpiResultMapper kpiResultMapper;

    @Mock
    private KpiFormulaService kpiFormulaService;

    @Mock
    private PerfEventPublisher perfEventPublisher;

    @InjectMocks
    private KpiCalcService kpiCalcService;

    @Test
    @DisplayName("[Red] calcScheme 成功完成后发布 KpiCalcCompletedEvent，字段齐全")
    void calcScheme_completes_publishesEvent() {
        String schemeCode = "TEST_Q41_SCHEME";
        String cycleType = "QUARTERLY";
        LocalDate cycleDate = LocalDate.of(2026, 3, 31);
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        String version = "V_Q41";

        // 宽表 2 位员工
        when(empIndexResultMapper.selectDistinctEmpIds(asOfDate, version))
                .thenReturn(List.of("E_Q41_A", "E_Q41_B"));

        // 方案 + 单 item 能走通 calcSingleEmp
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_Q41");
        scheme.setSchemeCode(schemeCode);
        scheme.setStatus("ACTIVE");
        when(kpiSchemeService.getBySchemeCodeOrNull(schemeCode)).thenReturn(Optional.of(scheme));

        PerfKpiItem item = new PerfKpiItem();
        item.setId("IT_Q41");
        item.setSchemeId("S_Q41");
        item.setMetricCode("M_Q41");
        item.setWeight(new BigDecimal("100"));
        item.setMultiplier(new BigDecimal("1"));
        item.setMinScore(BigDecimal.ZERO);
        item.setMaxScore(new BigDecimal("100"));
        when(kpiItemService.listBySchemeId("S_Q41")).thenReturn(List.of(item));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_Q41");
        def.setValSlot(7);
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        when(metricDefService.getByCodeOrNull("M_Q41")).thenReturn(def);

        when(empIndexResultMapper.selectSlotValue(anyString(), eq(asOfDate), eq(version), eq(7)))
                .thenReturn(new BigDecimal("80"));
        when(kpiFormulaService.eval(anyString(), any())).thenReturn(new BigDecimal("80"));

        // When
        int success = kpiCalcService.calcScheme(schemeCode, cycleType, cycleDate, asOfDate, version);

        // Then
        assertThat(success).isEqualTo(2);
        ArgumentCaptor<KpiCalcCompletedEvent> captor = ArgumentCaptor.forClass(KpiCalcCompletedEvent.class);
        verify(perfEventPublisher).publish(captor.capture());
        KpiCalcCompletedEvent ev = captor.getValue();
        assertThat(ev.getSchemeCode()).isEqualTo(schemeCode);
        assertThat(ev.getCycleType()).isEqualTo(cycleType);
        assertThat(ev.getCycleDate()).isEqualTo(cycleDate);
        assertThat(ev.getAsOfDate()).isEqualTo(asOfDate);
        assertThat(ev.getVersion()).isEqualTo(version);
        assertThat(ev.getEmpCount()).isEqualTo(2);
        assertThat(ev.topic()).isEqualTo("performance.kpi-calc.completed.v1");
        assertThat(ev.getEventId()).hasSize(32).doesNotContain("-");
        assertThat(ev.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("[Red] calcScheme 宽表无员工数据时不发布事件")
    void calcScheme_noEmployees_doesNotPublishEvent() {
        when(empIndexResultMapper.selectDistinctEmpIds(any(), anyString()))
                .thenReturn(List.of());

        int success = kpiCalcService.calcScheme("TEST_Q41_EMPTY", "MONTHLY",
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1), "V_EMPTY");

        assertThat(success).isZero();
        verify(perfEventPublisher, never()).publish(any());
        // 并未走 Scheme / Mapper 的 insert 路径
        verify(kpiResultMapper, never()).insert(any());
        verify(kpiSchemeService, never()).getBySchemeCodeOrNull(anyString());
    }

    @Test
    @DisplayName("[Red] calcScheme 所有员工均失败 success=0 时仍发布事件（empCount=0）")
    void calcScheme_allFail_stillPublishesEventWithZeroCount() {
        String schemeCode = "TEST_Q41_ALL_FAIL";
        LocalDate cycleDate = LocalDate.of(2026, 3, 31);
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        String version = "V_ALLFAIL";

        when(empIndexResultMapper.selectDistinctEmpIds(asOfDate, version))
                .thenReturn(List.of("E_X"));
        // 方案不存在让 calcSingleEmp 抛异常 → 所有员工失败，但 calcScheme 吞掉继续
        when(kpiSchemeService.getBySchemeCodeOrNull(schemeCode)).thenReturn(Optional.empty());

        int success = kpiCalcService.calcScheme(schemeCode, "MONTHLY", cycleDate, asOfDate, version);

        assertThat(success).isZero();
        ArgumentCaptor<KpiCalcCompletedEvent> captor = ArgumentCaptor.forClass(KpiCalcCompletedEvent.class);
        verify(perfEventPublisher).publish(captor.capture());
        KpiCalcCompletedEvent ev = captor.getValue();
        assertThat(ev.getEmpCount()).isZero();
        assertThat(ev.getSchemeCode()).isEqualTo(schemeCode);
    }
}
