package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import com.bank.branch.platform.workflow.api.dto.CancelProcessReqDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessSubmitReqDTO;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.service.ProcessCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProcessCommandController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProcessCommandControllerTest {

    @Mock
    private ProcessCommandService processCommandService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProcessCommandController(processCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void submitProcess_shouldReturn200() throws Exception {
        ProcessSubmitReqDTO req = new ProcessSubmitReqDTO();
        req.setBizType("LOAN");
        req.setBizId("LA20260414001");
        req.setBusinessKey("LOAN:LA20260414001");
        req.setProcessDefinitionKey("loan_approve_v1");
        req.setTitle("资产投放申请-提交");
        req.setVariables(Map.of("amount", 1000));

        when(processCommandService.submitProcess(any(ProcessSubmitReqDTO.class)))
                .thenReturn(new WorkflowLaunchResp("PID_001", "LOAN:LA20260414001", "TASK_001"));

        mockMvc.perform(post("/api/workflow/processes/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processInstanceId").value("PID_001"));
    }

    @Test
    void submitProcess_missingBizId_returns400() throws Exception {
        ProcessSubmitReqDTO req = new ProcessSubmitReqDTO();
        req.setBizType("LOAN");
        req.setBusinessKey("LOAN:LA20260414001");
        req.setProcessDefinitionKey("loan_approve_v1");
        req.setTitle("资产投放申请-提交");

        mockMvc.perform(post("/api/workflow/processes/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelProcess_shouldReturn200() throws Exception {
        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason("发起人撤回");
        doNothing().when(processCommandService).cancelProcess(anyString(), any(CancelProcessReqDTO.class));

        mockMvc.perform(post("/api/workflow/processes/PID_001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void cancelProcess_permissionDenied_returns403() throws Exception {
        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason("发起人撤回");
        doThrow(new PermissionDeniedException("AUTH-40305", "非流程发起人不可撤回"))
                .when(processCommandService).cancelProcess(anyString(), any(CancelProcessReqDTO.class));

        mockMvc.perform(post("/api/workflow/processes/PID_001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40305"));
    }

    @Test
    void cancelProcess_processFinished_returnsBizError() throws Exception {
        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason("发起人撤回");
        doThrow(new BizException("WF-40905", "流程实例不存在或已结束"))
                .when(processCommandService).cancelProcess(anyString(), any(CancelProcessReqDTO.class));

        mockMvc.perform(post("/api/workflow/processes/PID_001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40905"));
    }
}
