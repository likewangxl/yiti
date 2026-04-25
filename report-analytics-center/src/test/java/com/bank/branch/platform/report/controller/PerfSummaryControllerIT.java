package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 绩效汇总 C.3 端到端 IT（Task M3.2.1 + M3.2.2，Red→Green）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>GET /api/reports/perf-summary：返回分页 + KPI 总分</li>
 *   <li>POST /api/reports/perf-summary/export：返回 PENDING 状态任务</li>
 * </ol>
 */
class PerfSummaryControllerIT extends BaseControllerIT {

    @Test
    void getPerfSummary_emp_returns200_withTotalScore() throws Exception {
        when(kpiApi.getCurrentKpiTotal(eq("E001"), eq("MONTHLY")))
                .thenReturn(new BigDecimal("88.5"));

        mvc.perform(get("/api/reports/perf-summary")
                        .param("dim", "EMP")
                        .param("subjectIds", "E001")
                        .param("cycleType", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].subjectId").value("E001"))
                .andExpect(jsonPath("$.page.records[0].totalScore").value(88.5));
    }

    @Test
    void submitPerfSummaryExport_returnsPending_withTaskId() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_HR");

        String body = """
                {
                  "dim": "EMP",
                  "subjectIds": ["E001", "E002"],
                  "cycleType": "MONTHLY"
                }
                """;

        mvc.perform(post("/api/reports/perf-summary/export")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty());
    }
}
