package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportReqDTO;
import com.bank.branch.platform.redengine.service.ReTaskExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 任务异步导出 REST 契约测试，使用 standalone MockMvc。 */
@ExtendWith(MockitoExtension.class)
class ReTaskExportControllerTest {

    @Mock
    private ReTaskExportService service;
    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReTaskExportController(service, currentUserApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createExportUsesCurrentEmployeeAndReturnsQueuedJob() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        ReTaskExportRespDTO response = new ReTaskExportRespDTO();
        response.setExportId("RTE_1");
        response.setStatus("QUEUED");
        when(service.createExport(eq(7L), any(ReTaskExportReqDTO.class), eq("E001")))
                .thenReturn(response);

        mockMvc.perform(post("/api/re/tasks/7/exports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReTaskExportReqDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.exportId").value("RTE_1"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verify(service).createExport(eq(7L), any(ReTaskExportReqDTO.class), eq("E001"));
    }

    @Test
    void statusEndpointReturnsExportStatus() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        ReTaskExportRespDTO response = new ReTaskExportRespDTO();
        response.setExportId("RTE_1");
        response.setStatus("SUCCEEDED");
        when(service.getStatus("RTE_1", "E001")).thenReturn(response);

        mockMvc.perform(get("/api/re/task-exports/RTE_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        verify(service).getStatus("RTE_1", "E001");
    }

    @Test
    void downloadEndpointReturnsZipBytes() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(service.download("RTE_1", "E001")).thenReturn(new byte[]{'P', 'K'});

        mockMvc.perform(get("/api/re/task-exports/RTE_1/download"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{'P', 'K'}))
                .andExpect(content().contentType("application/zip"));

        verify(service).download("RTE_1", "E001");
    }
}
