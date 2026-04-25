package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 触达任务监控 C.2 端到端 IT（Task M3.1.1 + M3.1.2，Red）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>GET /api/reports/touch-task-summary：返回分页汇总（page.total=1）</li>
 *   <li>POST /api/reports/touch-task-summary/export：返回 PENDING 状态任务（M5 Worker 未启用）</li>
 * </ol>
 */
class TouchSummaryControllerIT extends BaseControllerIT {

    @Test
    void getTouchSummary_returns200_withPageTotal() throws Exception {
        // Arrange：上游 TouchTaskQueryApi 返回单个汇总
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR001");
        TouchTaskSummaryDTO upstream = new TouchTaskSummaryDTO();
        upstream.setOrgId("BR001");
        upstream.setOrgName("分行 A");
        upstream.setTotalCount(100L);
        upstream.setSuccessCount(70L);
        upstream.setCancelledCount(30L);
        when(touchTaskQueryApi.getOrgTouchSummary(eq("BR001"), anyString(), anyString()))
                .thenReturn(upstream);

        mvc.perform(get("/api/reports/touch-task-summary")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-25")
                        .param("orgId", "BR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].orgCode").value("BR001"))
                .andExpect(jsonPath("$.page.records[0].totalTask").value(100))
                .andExpect(jsonPath("$.page.records[0].successRate").value(0.7));
    }

    @Test
    void submitTouchSummaryExport_returnsPending_withTaskId() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES");

        String body = """
                {
                  "startDate": "2026-04-01",
                  "endDate": "2026-04-25",
                  "orgId": "BR001"
                }
                """;

        mvc.perform(post("/api/reports/touch-task-summary/export")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty());
    }
}
