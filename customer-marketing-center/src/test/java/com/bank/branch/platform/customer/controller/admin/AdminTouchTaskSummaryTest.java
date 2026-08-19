package com.bank.branch.platform.customer.controller.admin;

import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminTouchTaskController.summary 集成测试（GET /api/admin/touch-tasks/summary）。
 */
class AdminTouchTaskSummaryTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TouchTaskQueryApi touchTaskQueryApi;

    @Test
    @WithMockEmpContext(empId = "ADMIN001", roleCodes = {"R_ADMIN"}, systemAdmin = true)
    void summary_validOrg_returns200WithCounts() throws Exception {
        TouchTaskSummaryDTO summary = new TouchTaskSummaryDTO();
        summary.setOrgId("ORG_SZ_001");
        summary.setOrgName("深圳分行");
        summary.setTotalCount(120L);
        summary.setPendingCount(15L);
        summary.setInProgressCount(20L);
        summary.setSuccessCount(80L);
        summary.setCancelledCount(5L);
        summary.setSlaWarningCount(3L);
        summary.setAvgDurationHours(48.5);

        when(touchTaskQueryApi.getOrgTouchSummary(eq("ORG_SZ_001"), eq(null), eq(null)))
                .thenReturn(summary);

        mockMvc.perform(get("/api/admin/touch-tasks/summary")
                        .param("orgCode", "ORG_SZ_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.orgId").value("ORG_SZ_001"))
                .andExpect(jsonPath("$.data.totalCount").value(120))
                .andExpect(jsonPath("$.data.pendingCount").value(15))
                .andExpect(jsonPath("$.data.inProgressCount").value(20))
                .andExpect(jsonPath("$.data.successCount").value(80))
                .andExpect(jsonPath("$.data.slaWarningCount").value(3));
    }

    @Test
    @WithMockEmpContext(empId = "ADMIN001", roleCodes = {"R_ADMIN"}, systemAdmin = true)
    void summary_withDateRange_passesParametersThrough() throws Exception {
        TouchTaskSummaryDTO summary = new TouchTaskSummaryDTO();
        summary.setOrgId("ORG_SZ_001");
        summary.setTotalCount(50L);

        when(touchTaskQueryApi.getOrgTouchSummary(
                eq("ORG_SZ_001"), eq("2026-04-01"), eq("2026-04-30")))
                .thenReturn(summary);

        mockMvc.perform(get("/api/admin/touch-tasks/summary")
                        .param("orgCode", "ORG_SZ_001")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalCount").value(50));
    }

    @Test
    @WithMockEmpContext(empId = "M10001", orgCode = "ORG001")
    void summary_nonAdminManager_shouldForceCurrentOrgScope() throws Exception {
        TouchTaskSummaryDTO summary = new TouchTaskSummaryDTO();
        summary.setOrgId("ORG001");
        when(touchTaskQueryApi.getOrgTouchSummary(eq("ORG001"), eq(null), eq(null)))
                .thenReturn(summary);

        mockMvc.perform(get("/api/admin/touch-tasks/summary")
                        .param("orgCode", "ORG_OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").value("ORG001"));
    }

    @Test
    @WithMockEmpContext(empId = "ADMIN001", roleCodes = {"R_ADMIN"}, systemAdmin = true)
    void summary_missingOrgCode_returns400() throws Exception {
        // orgCode 是 @RequestParam 默认 required=true，缺省返回真 HTTP 400
        mockMvc.perform(get("/api/admin/touch-tasks/summary"))
                .andExpect(status().isBadRequest());
    }
}
