package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import com.bank.branch.platform.performance.service.engine.StatShowSqlRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetricCalcServiceSummaryTest {
    private MetricCalcService service;
    private MetricDefService defs;
    private OrgMetricSummaryCalculator calculator;
    private PerfRunTaskMapper tasks;
    private OrgIndexResultMapper orgMapper;

    @BeforeEach
    void setUp() {
        defs = mock(MetricDefService.class);
        tasks = mock(PerfRunTaskMapper.class);
        orgMapper = mock(OrgIndexResultMapper.class);
        service = new MetricCalcService(defs, mock(SqlExecutor.class), new StatShowSqlRouter(), mock(GroovyExecutor.class),
                mock(EmpIndexResultMapper.class), orgMapper, mock(CustIndexResultMapper.class), tasks, null,
                mock(SubjectFetcher.class), mock(ApplicationEventPublisher.class), null);
        calculator = mock(OrgMetricSummaryCalculator.class);
        service.setOrgMetricSummaryCalculator(calculator);
    }

    @Test
    void summaryPersistsZeroAndPublishesSuccess() {
        PerfMetricDef def = def("M_SUM", "SUMMARY");
        when(defs.getByCodeOrNull("M_SUM")).thenReturn(def);
        Map<String, BigDecimal> values = new LinkedHashMap<>(); values.put("O1", BigDecimal.ZERO); values.put("O2", BigDecimal.ONE);
        when(calculator.calculate(eq(def), any(LocalDate.class), eq("v1"))).thenReturn(values);
        service.calcMetric("M_SUM", LocalDate.of(2026, 11, 9), "v1");
        verify(orgMapper).clearSlot(any(), eq("v1"), eq(220));
        verify(orgMapper).insertSlotValue("O1", LocalDate.of(2026, 11, 9), "v1", 220, BigDecimal.ZERO);
        verify(tasks).updateStatusWithParams(anyString(), eq("SUCCESS"), isNull(), contains("subjectSuccess"));
        verify(calculator).calculate(eq(def), any(), eq("v1"));
    }

    @Test
    void summaryNullIsPartialAndCalculatorFailureMarksFailed() {
        PerfMetricDef def = def("M_SUM", "SUMMARY"); when(defs.getByCodeOrNull("M_SUM")).thenReturn(def);
        Map<String, BigDecimal> partial = new LinkedHashMap<>(); partial.put("O1", null); partial.put("O2", BigDecimal.ONE);
        when(calculator.calculate(eq(def), any(), eq("v1"))).thenReturn(partial);
        service.calcMetric("M_SUM", LocalDate.of(2026, 11, 9), "v1");
        verify(tasks).updateStatusWithParams(anyString(), eq("PARTIAL_FAILED"), isNull(), contains("subjectFailed"));
        reset(tasks);
        when(calculator.calculate(eq(def), any(), eq("v1"))).thenThrow(new IllegalStateException("bad org mapping"));
        assertThrows(RuntimeException.class, () -> service.calcMetric("M_SUM", LocalDate.of(2026, 11, 9), "v1"));
        verify(tasks).updateStatus(anyString(), eq("FAILED"), contains("bad org mapping"));
    }

    @Test
    void procStillRejected() {
        PerfMetricDef def = def("M_PROC", "PROC"); when(defs.getByCodeOrNull("M_PROC")).thenReturn(def);
        assertThrows(RuntimeException.class, () -> service.calcMetric("M_PROC", LocalDate.of(2026, 11, 9), "v1"));
        verifyNoInteractions(calculator);
    }

    private static PerfMetricDef def(String code, String logic) {
        PerfMetricDef d = new PerfMetricDef(); d.setMetricCode(code); d.setCalcLogicType(logic); d.setBaseDim("ORG"); d.setValSlot(220); d.setStatus("ACTIVE"); d.setDeleted(0); d.setSummaryRule("SUM"); return d;
    }
}
