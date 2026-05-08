package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 仪表盘 3 接口端到端 IT（Task M2.3.1，Red）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>GET /dashboard/president：R_PRESIDENT 角色 → 200 + dataDate 字段</li>
 *   <li>GET /dashboard/org/{orgCode}：返回机构汇总 → 200 + orgCode 字段</li>
 *   <li>GET /dashboard/emp/{empId}：返回员工 KPI → 200 + empId 字段</li>
 * </ol>
 */
class DashboardControllerIT extends BaseControllerIT {

    @Test
    void getPresidentDashboard_returns200_withFiveSections() throws Exception {
        // 生产代码用 ROLE_ID 校验（"R_PRESIDENT"），早期 mock 误用 getCurrentRoleCodes
        when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(orgApi.getOrgSubtreeCodes("ORG001")).thenReturn(Set.of("ORG001"));
        when(metricApi.getOrgMetricValues(anyString(), any(), anyList()))
                .thenReturn(Map.of("DEP_BAL_ORG", new BigDecimal("100")));
        when(customerQueryApi.searchCustomers(any(), eq(10))).thenReturn(java.util.List.of());
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG001");
        org.setOrgName("总行");
        when(orgApi.getOrg("ORG001")).thenReturn(org);

        mvc.perform(get("/api/reports/dashboard/president")
                        .param("dataDate", "2026-04-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataDate").value("2026-04-09"))
                .andExpect(jsonPath("$.data.summaryMetrics").exists())
                .andExpect(jsonPath("$.data.depositTrend").exists())
                .andExpect(jsonPath("$.data.loanTrend").exists());
    }

    @Test
    void getPresidentDashboard_withExplicitOrgCodeParam_shouldOverrideCurrentUser() throws Exception {
        // V1.14 # 2 RED：?orgCode=ORG_FROM_PARAM 必须覆盖 currentUserApi.getCurrentOrgCode()
        when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_DEFAULT");
        when(orgApi.getOrgSubtreeCodes("ORG_FROM_PARAM")).thenReturn(Set.of("ORG_FROM_PARAM"));
        OrgDTO orgFromParam = new OrgDTO();
        orgFromParam.setOrgCode("ORG_FROM_PARAM");
        orgFromParam.setOrgName("入参机构");
        when(orgApi.getOrg("ORG_FROM_PARAM")).thenReturn(orgFromParam);
        // 仅 ORG_FROM_PARAM 的 metric 有特定值，证明走的是入参 orgCode
        when(metricApi.getOrgMetricValues(eq("ORG_FROM_PARAM"), any(), anyList()))
                .thenReturn(Map.of("DEP_BAL_ORG", new BigDecimal("999")));
        when(customerQueryApi.searchCustomers(any(), eq(10))).thenReturn(java.util.List.of());

        mvc.perform(get("/api/reports/dashboard/president")
                        .param("orgCode", "ORG_FROM_PARAM")
                        .param("dataDate", "2026-04-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stats").isArray())
                .andExpect(jsonPath("$.data.stats.length()").value(5))
                .andExpect(jsonPath("$.data.stats[0].metricCode").value("DEP_BAL_ORG"))
                .andExpect(jsonPath("$.data.stats[0].label").value("存款日均"))
                .andExpect(jsonPath("$.data.stats[0].value").value(999));
    }

    @Test
    void getPresidentDashboard_omittedOrgCodeParam_shouldFallbackToCurrentUser() throws Exception {
        // V1.14 # 2 RED：不传 orgCode 时退化到 currentUserApi.getCurrentOrgCode()，保持向后兼容
        when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_FALLBACK");
        when(orgApi.getOrgSubtreeCodes("ORG_FALLBACK")).thenReturn(Set.of("ORG_FALLBACK"));
        OrgDTO orgFallback = new OrgDTO();
        orgFallback.setOrgCode("ORG_FALLBACK");
        orgFallback.setOrgName("Fallback 机构");
        when(orgApi.getOrg("ORG_FALLBACK")).thenReturn(orgFallback);
        when(metricApi.getOrgMetricValues(eq("ORG_FALLBACK"), any(), anyList()))
                .thenReturn(Map.of("DEP_BAL_ORG", new BigDecimal("777")));
        when(customerQueryApi.searchCustomers(any(), eq(10))).thenReturn(java.util.List.of());

        mvc.perform(get("/api/reports/dashboard/president")
                        .param("dataDate", "2026-04-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stats.length()").value(5))
                .andExpect(jsonPath("$.data.stats[0].value").value(777));
    }

    @Test
    void getOrgDashboard_returns200_withOrgCode() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_LEADER");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG002");
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG002");
        org.setOrgName("XX 支行");
        when(orgApi.getOrg("ORG002")).thenReturn(org);
        when(metricApi.getOrgMetricValues(eq("ORG002"), any(), anyList()))
                .thenReturn(Map.of("DEP_BAL_ORG", new BigDecimal("500")));

        mvc.perform(get("/api/reports/dashboard/org/{orgCode}", "ORG002")
                        .param("dataDate", "2026-04-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orgCode").value("ORG002"))
                .andExpect(jsonPath("$.data.orgName").value("XX 支行"));
    }

    @Test
    void getEmpDashboard_returns200_withEmpId() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(metricApi.getEmpMetricValues(eq("E001"), any(), anyList()))
                .thenReturn(Map.of("KPI_TOTAL", new BigDecimal("88.5")));

        mvc.perform(get("/api/reports/dashboard/emp/{empId}", "E001")
                        .param("dataDate", "2026-04-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.empId").value("E001"));
    }
}
