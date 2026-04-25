package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.dto.resp.SavedQueryDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.SavedQuerySummaryDTO;
import com.bank.branch.platform.report.service.SavedQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SavedQueryController GET 端点 IT（Task M1.4.1）.
 */
class SavedQueryControllerListIT extends BaseControllerIT {

    @MockBean
    private SavedQueryService savedQueryService;

    @Test
    void listMine_returnsArray() throws Exception {
        when(savedQueryService.listMine(any())).thenReturn(List.of(
                SavedQuerySummaryDTO.builder()
                        .id("Q1").name("方案A").dim("EMP")
                        .createdTime(LocalDateTime.now())
                        .updatedTime(LocalDateTime.now())
                        .build()));
        mvc.perform(get("/api/reports/saved-queries").param("dim", "EMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].id").value("Q1"));
    }

    @Test
    void getDetail_returnsDetail() throws Exception {
        when(savedQueryService.getDetail("Q1")).thenReturn(
                SavedQueryDetailRespDTO.builder()
                        .id("Q1").name("方案A").dim("EMP")
                        .subjectIds("[\"E001\"]").metricCodes("[\"M\"]").version(1)
                        .build());
        mvc.perform(get("/api/reports/saved-queries/Q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("Q1"))
                .andExpect(jsonPath("$.data.version").value(1));
    }
}
