package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.service.SavedQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SavedQueryController POST/PUT/DELETE 端点 IT（Task M1.5.1 / M1.5.2 / M1.5.3）.
 */
class SavedQueryControllerCudIT extends BaseControllerIT {

    @MockBean
    private SavedQueryService savedQueryService;

    @Test
    void saveQuery_returnsId() throws Exception {
        when(savedQueryService.saveQuery(any())).thenReturn("Q-NEW");
        String body = """
                {
                  "name": "我的方案",
                  "dim": "EMP",
                  "subjectIds": "[\\"E001\\"]",
                  "metricCodes": "[\\"M\\"]"
                }
                """;
        mvc.perform(post("/api/reports/saved-queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("Q-NEW"));
    }

    @Test
    void updateQuery_succeeds() throws Exception {
        String body = """
                {
                  "name": "新名",
                  "expectedVersion": 1
                }
                """;
        mvc.perform(put("/api/reports/saved-queries/Q1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        verify(savedQueryService, times(1)).updateQuery(eq("Q1"), any());
    }

    @Test
    void updateQuery_missingExpectedVersion_returns400() throws Exception {
        String body = """
                {
                  "name": "新名"
                }
                """;
        mvc.perform(put("/api/reports/saved-queries/Q1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deleteQuery_succeeds() throws Exception {
        mvc.perform(delete("/api/reports/saved-queries/Q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        verify(savedQueryService, times(1)).deleteQuery("Q1");
    }
}
