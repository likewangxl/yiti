package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrgMetricSummaryCalculatorTest {
    private final MetricDefService defs = mock(MetricDefService.class);
    private final OrgApi orgApi = mock(OrgApi.class);
    private final EmpIndexResultMapper emp = mock(EmpIndexResultMapper.class);
    private final OrgIndexResultMapper org = mock(OrgIndexResultMapper.class);
    private final LocalDate day = LocalDate.of(2026, 11, 9);
    private final OrgMetricSummaryCalculator calculator = new OrgMetricSummaryCalculator(defs, orgApi, emp, org);

    @Test
    void directScopeSumsOnlyEmployeesOfEachOrgAndPreservesZero() {
        PerfMetricDef def = definition("ORG_SUM", "YUAN", "ORG_DIRECT", "[\"EMP_A\"]");
        when(defs.getByCodeOrNull("EMP_A")).thenReturn(ref("EMP_A", "YUAN", 11));
        when(org.selectDistinctOrgCodes(day, "v1")).thenReturn(List.of("O1", "O2"));
        when(orgApi.getOrgsByCodes(List.of("O1", "O2"))).thenReturn(orgs("O1", "O2"));
        when(orgApi.listUniqueUserOrgs(anyCollection())).thenReturn(mapping("e1", "O1", "e2", "O2"));
        when(emp.selectDistinctEmpIds(day, "v1")).thenReturn(List.of("e1", "e2"));
        when(emp.selectSlotValuesByEmps(anyList(), eq(day), eq("v1"), eq(11)))
                .thenReturn(List.of(row("e1", BigDecimal.ZERO), row("e2", new BigDecimal("4"))));

        assertEquals(new BigDecimal("0"), calculator.calculate(def, day, "v1").get("O1"));
        assertEquals(new BigDecimal("4"), calculator.calculate(def, day, "v1").get("O2"));
    }

    @Test
    void treeScopeRejectsDuplicateEmployeeMappingsAndMissingValuesBecomeNull() {
        PerfMetricDef def = definition("ORG_SUM", "YUAN", "ORG_TREE", "[\"EMP_A\"]");
        when(defs.getByCodeOrNull("EMP_A")).thenReturn(ref("EMP_A", "YUAN", 11));
        when(org.selectDistinctOrgCodes(day, "v1")).thenReturn(List.of("O1"));
        when(orgApi.getOrgsByCodes(List.of("O1"))).thenReturn(orgs("O1"));
        when(orgApi.getOrgSubtreeCodes("O1")).thenReturn(java.util.Set.of("O1", "O1-CHILD"));
        when(orgApi.listUniqueUserOrgs(anyCollection())).thenReturn(mapping("e1", "O1", "e1", "O1-CHILD"));
        when(emp.selectDistinctEmpIds(day, "v1")).thenReturn(List.of("e1"));
        assertThrows(RuntimeException.class, () -> calculator.calculate(def, day, "v1"));
    }

    private static PerfMetricDef definition(String code, String unit, String scope, String refs) {
        PerfMetricDef d = new PerfMetricDef(); d.setMetricCode(code); d.setBaseDim("ORG"); d.setSummaryRule("SUM");
        d.setCalcLogicType("SUMMARY"); d.setRefMetricCodes(refs); d.setUnit(unit); d.setStatus("ACTIVE"); d.setDeleted(0);
        d.setValSlot(201); d.setMetricDesc("{\"_summaryOrgScope\":\"" + scope + "\"}"); return d;
    }
    private static PerfMetricDef ref(String code, String unit, int slot) { PerfMetricDef d=new PerfMetricDef(); d.setMetricCode(code); d.setBaseDim("EMP"); d.setUnit(unit); d.setValSlot(slot); d.setStatus("ACTIVE"); d.setDeleted(0); return d; }
    private static EmpMetricValueRow row(String empId, BigDecimal value) { EmpMetricValueRow r=new EmpMetricValueRow(); r.setEmpId(empId); r.setMetricValue(value); return r; }
    private static List<OrgDTO> orgs(String... codes) { return java.util.Arrays.stream(codes).map(c->{OrgDTO o=new OrgDTO();o.setOrgCode(c);return o;}).toList(); }
    private static List<UniqueUserOrgDTO> mapping(String... values) { java.util.ArrayList<UniqueUserOrgDTO> out=new java.util.ArrayList<>(); for(int i=0;i<values.length;i+=2){UniqueUserOrgDTO x=new UniqueUserOrgDTO();x.setEmpId(values[i]);x.setOrgCode(values[i+1]);out.add(x);} return out; }
}
