package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.dto.resp.DynamicQueryRespDTO;
import com.bank.branch.platform.report.dto.resp.MetricColumnDTO;
import com.bank.branch.platform.report.service.DynamicQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A.2 POST /api/reports/dynamic-query Controller IT（Task M1.2.1）.
 */
class DynamicQueryControllerIT extends BaseControllerIT {

    @MockBean
    private DynamicQueryService dynamicQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void dynamicQuery_returns200WithRows() throws Exception {
        DynamicQueryRespDTO mockResp = DynamicQueryRespDTO.builder()
                .dim("EMP")
                .dataDate(LocalDate.of(2026, 4, 1))
                .columns(List.of(MetricColumnDTO.builder().metricCode("M_A").metricName("指标A").build()))
                .rows(List.of(Map.of("subjectId", "E001", "M_A", 100)))
                .rowCount(1)
                .build();
        when(dynamicQueryService.execute(any())).thenReturn(mockResp);

        String body = """
                {
                  "dim": "EMP",
                  "subjectIds": ["E001"],
                  "metricCodes": ["M_A"],
                  "dataDate": "2026-04-01"
                }
                """;
        mvc.perform(post("/api/reports/dynamic-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.rowCount").value(1));
    }

    @Test
    void dynamicQuery_invalidEmptyMetricCodes_returns400() throws Exception {
        String body = """
                {
                  "dim": "EMP",
                  "subjectIds": ["E001"],
                  "metricCodes": [],
                  "dataDate": "2026-04-01"
                }
                """;
        mvc.perform(post("/api/reports/dynamic-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }
}
