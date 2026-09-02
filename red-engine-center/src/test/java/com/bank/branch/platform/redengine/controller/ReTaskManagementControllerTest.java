package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateRespDTO;
import com.bank.branch.platform.redengine.service.ReTaskManagementService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 任务管理 REST 入口契约测试，使用 standalone MockMvc。 */
@ExtendWith(MockitoExtension.class)
class ReTaskManagementControllerTest {

    @Mock
    private ReTaskManagementService service;
    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReTaskManagementController(service, currentUserApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createTask_delegatesWithCurrentEmployeeAndReturnsTaskId() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-001");
        ReTaskCreateRespDTO response = new ReTaskCreateRespDTO();
        response.setTaskId(99L);
        when(service.createAndPublish(any(ReTaskCreateReqDTO.class), any(String.class))).thenReturn(response);

        String body = "{"
                + "\"title\":\"任务\",\"description\":\"说明\","
                + "\"taskNature\":\"SCHEDULED\",\"businessType\":\"GENERAL\","
                + "\"cycleType\":\"MONTH_START\",\"durationDays\":3,"
                + "\"requiresFile\":false,\"targets\":[{\"targetType\":\"ALL_BRANCH\"}]"
                + "}";

        mockMvc.perform(post("/api/re/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value(99));

        verify(service).createAndPublish(any(ReTaskCreateReqDTO.class), org.mockito.ArgumentMatchers.eq("ORG-001"));
    }
}
