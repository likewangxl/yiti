package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReHomeBranchRankingDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeSummaryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReWarningPoolDTO;
import com.bank.branch.platform.redengine.service.ReHomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 首页汇总、预警池和逾期扣分 REST 入口契约测试。 */
@ExtendWith(MockitoExtension.class)
class ReHomeControllerTest {

    @Mock
    private ReHomeService service;
    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReHomeController(service, currentUserApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void summary_usesCurrentEmployeeAndReturnsRoleScopedData() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("EMP-001");
        ReHomeSummaryDTO summary = new ReHomeSummaryDTO();
        summary.setMode("ORGANIZATION");
        summary.setQuarter("2026-Q3");
        summary.setBranchName("七支部");
        summary.setBranchScore(new BigDecimal("88"));
        summary.setBranchRank(2);
        when(service.getSummary("EMP-001")).thenReturn(summary);

        mockMvc.perform(get("/api/re/home/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.mode").value("ORGANIZATION"))
                .andExpect(jsonPath("$.data.quarter").value("2026-Q3"))
                .andExpect(jsonPath("$.data.organizationName").value("七支部"))
                .andExpect(jsonPath("$.data.organizationScore").value(88))
                .andExpect(jsonPath("$.data.organizationRank").value(2));

        verify(service).getSummary("EMP-001");
    }

    @Test
    void ranking_returnsCurrentQuarterRanking() throws Exception {
        ReHomeBranchRankingDTO row = new ReHomeBranchRankingDTO();
        row.setBranchId(7L);
        row.setBranchName("七支部");
        row.setRank(1);
        when(currentUserApi.getCurrentEmpId()).thenReturn("EMP-001");
        when(service.getCurrentQuarterRanking("EMP-001")).thenReturn(List.of(row));

        mockMvc.perform(get("/api/re/home/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].branchId").value(7))
                .andExpect(jsonPath("$.data[0].rank").value(1));
    }

    @Test
    void warningPool_returnsCombinedRedAndYellowData() throws Exception {
        ReWarningPoolDTO pool = new ReWarningPoolDTO();
        pool.setQuarter("2026-Q3");
        pool.setPreviousQuarter("2026-Q2");
        when(service.getWarningPool()).thenReturn(pool);

        mockMvc.perform(get("/api/re/home/warning-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quarter").value("2026-Q3"))
                .andExpect(jsonPath("$.data.previousQuarter").value("2026-Q2"));
    }

    @Test
    void overduePage_isPagedAndPassesCurrentEmployee() throws Exception {
        ReTaskOverdueItemDTO row = new ReTaskOverdueItemDTO();
        row.setAssignmentId(30L);
        when(currentUserApi.getCurrentEmpId()).thenReturn("ADMIN-001");
        when(service.pageOverdue(any(), eq("ADMIN-001")))
                .thenReturn(PageResult.of(1, 20, 1, List.of(row)));

        mockMvc.perform(get("/api/re/home/overdue")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].assignmentId").value(30));

        verify(service).pageOverdue(any(), eq("ADMIN-001"));
    }

    @Test
    void executeOverdue_requiresReasonAndPassesCurrentEmployee() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ADMIN-001");
        ReTaskDeductionActionRespDTO response = new ReTaskDeductionActionRespDTO();
        response.setAssignmentId(30L);
        response.setIdempotent(false);
        when(service.executeOverdue(any(), eq("ADMIN-001"))).thenReturn(response);

        mockMvc.perform(post("/api/re/home/overdue/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignmentId\":30,\"deductionPoints\":5,"
                                + "\"reason\":\"逾期未上报\",\"clientRequestId\":\"req-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignmentId").value(30));

        verify(service).executeOverdue(any(), eq("ADMIN-001"));
    }

    @Test
    void executeOverdue_withoutReason_isRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/re/home/overdue/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignmentId\":30}"))
                .andExpect(status().isBadRequest());
    }
}
