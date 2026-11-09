package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiCalcServiceDimensionTest {
    @Mock KpiSchemeService schemeService;
    @Mock KpiItemService itemService;
    @Mock MetricDefService metricService;
    @Mock EmpIndexResultMapper empMapper;
    @Mock KpiResultMapper resultMapper;
    @Mock KpiFormulaService formula;
    @Mock com.bank.branch.platform.performance.event.PerfEventPublisher events;
    @InjectMocks KpiCalcService service;

    @Test
    void declaredCustomerItemWithoutMetricDefinitionDoesNotBlockEmployeeCalculation() {
        PerfKpiScheme scheme = scheme();
        PerfKpiItem emp = item("EMP_ITEM", "EMP", "50");
        PerfKpiItem cust = item("MISSING_CUST", "CUST", "50");
        when(schemeService.getBySchemeCodeOrNull("S")).thenReturn(Optional.of(scheme));
        when(itemService.listBySchemeId("S1")).thenReturn(List.of(emp, cust));
        PerfMetricDef def = metric("EMP_ITEM", "EMP", 10);
        when(metricService.getByCodeOrNull("EMP_ITEM")).thenReturn(def);
        when(empMapper.selectSlotValue("E1", LocalDate.of(2026, 11, 9), "v1", 10)).thenReturn(new BigDecimal("80"));
        when(formula.eval(anyString(), anyMap())).thenReturn(new BigDecimal("80"));
        service.calcSingleEmp("E1", "S", "YEARLY", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 9), "v1");
        verify(metricService, atLeastOnce()).getByCodeOrNull("EMP_ITEM");
        verify(metricService, never()).getByCodeOrNull("MISSING_CUST");
        verify(resultMapper).insert(org.mockito.ArgumentMatchers.<com.bank.branch.platform.performance.entity.KpiResult>argThat(result -> result.getKpiTotalScore().compareTo(new BigDecimal("80")) == 0));
    }

    @Test
    void pureCustomerItemsAreRejectedWithoutEmployeeLookup() {
        PerfKpiScheme scheme = scheme();
        when(schemeService.getBySchemeCodeOrNull("S")).thenReturn(Optional.of(scheme));
        when(itemService.listBySchemeId("S1")).thenReturn(List.of(item("CUST_ITEM", "CUST", "100")));
        assertThatThrownBy(() -> service.calcSingleEmp("E1", "S", "YEARLY", LocalDate.now(), LocalDate.now(), "v1"))
                .isInstanceOf(com.bank.branch.platform.performance.exception.PerfException.class)
                .extracting("errorCode").isEqualTo(PerfErrorCode.KPI_WEIGHT_SUM_INVALID);
        verifyNoInteractions(metricService, empMapper);
    }

    @Test
    void declaredEmployeeItemWithCustomerDefinitionFailsClosed() {
        PerfKpiScheme scheme = scheme();
        when(schemeService.getBySchemeCodeOrNull("S")).thenReturn(Optional.of(scheme));
        when(itemService.listBySchemeId("S1")).thenReturn(List.of(item("CUST_DEF", "EMP", "100")));
        when(metricService.getByCodeOrNull("CUST_DEF")).thenReturn(metric("CUST_DEF", "CUST", 10));
        assertThatThrownBy(() -> service.calcSingleEmp("E1", "S", "YEARLY", LocalDate.now(), LocalDate.now(), "v1"))
                .isInstanceOf(com.bank.branch.platform.performance.exception.PerfException.class)
                .extracting("errorCode").isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
        verifyNoInteractions(empMapper);
    }

    private static PerfKpiScheme scheme() { PerfKpiScheme s=new PerfKpiScheme();s.setId("S1");s.setSchemeCode("S");s.setStatus("ACTIVE");return s; }
    private static PerfKpiItem item(String code,String dim,String weight){PerfKpiItem i=new PerfKpiItem();i.setMetricCode(code);i.setBaseDim(dim);i.setWeight(new BigDecimal(weight));i.setMultiplier(BigDecimal.ONE);return i;}
    private static PerfMetricDef metric(String code,String dim,int slot){PerfMetricDef d=new PerfMetricDef();d.setMetricCode(code);d.setBaseDim(dim);d.setValSlot(slot);d.setStatus("ACTIVE");d.setDeleted(0);return d;}
}
