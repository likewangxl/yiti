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
                .thenReturn(Map.of("DEP_BAL_ORG_DAILY", new BigDecimal("100")));
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
    void getOrgDashboard_returns200_withOrgCode() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_LEADER");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG002");
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG002");
        org.setOrgName("XX 支行");
        when(orgApi.getOrg("ORG002")).thenReturn(org);
        when(metricApi.getOrgMetricValues(eq("ORG002"), any(), anyList()))
                .thenReturn(Map.of("DEP_BAL_ORG_DAILY", new BigDecimal("500")));

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
