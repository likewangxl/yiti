package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApproveReqDTO;
import com.bank.branch.platform.workflow.api.dto.RejectReqDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.api.dto.TransferReqDTO;
import com.bank.branch.platform.workflow.service.TaskOperationService;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TodoQueryService todoQueryService;

    @Mock
    private TaskOperationService taskOperationService;

    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TaskController(todoQueryService, taskOperationService, currentUserApi))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void queryTodoList_shouldReturn200WithPageResult() throws Exception {
        // given
        TaskRespDTO dto = new TaskRespDTO();
        dto.setTaskId("T_001");
        dto.setBizType("LEAD");
        PageResult<TaskRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(todoQueryService.queryTodoList(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/workflow/tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void queryDoneList_shouldReturn200WithPageResult() throws Exception {
        // given
        TaskRespDTO dto = new TaskRespDTO();
        dto.setTaskId("T_002");
        PageResult<TaskRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(todoQueryService.queryDoneList(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/workflow/tasks/done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void getTaskDetail_shouldReturn200WithDetail() throws Exception {
        // given
        TaskDetailRespDTO detail = new TaskDetailRespDTO();
        TaskRespDTO taskInfo = new TaskRespDTO();
        taskInfo.setTaskId("T_003");
        detail.setTaskInfo(taskInfo);
        when(todoQueryService.getTaskDetail(anyString(), anyString())).thenReturn(detail);

        // when & then
        mockMvc.perform(get("/api/workflow/tasks/T_003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskInfo.taskId").value("T_003"));
    }

    @Test
    void claimTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).claimTask(anyString());

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void approveTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).approveTask(anyString(), any(ApproveReqDTO.class));

        ApproveReqDTO req = new ApproveReqDTO();
        req.setOpinion("同意");
        req.setFormData(Map.of("approved", true));

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void rejectTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).rejectTask(anyString(), any(RejectReqDTO.class));

        RejectReqDTO req = new RejectReqDTO();
        req.setOpinion("不符合要求");

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void transferTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).transferTask(anyString(), any(TransferReqDTO.class));

        TransferReqDTO req = new TransferReqDTO();
        req.setTargetEmpId("EMP002");
        req.setReason("出差交接");

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void claimTask_taskNotFound_returnsBizError() throws Exception {
        doThrow(new BizException("WF-40403", "任务不存在"))
            .when(taskOperationService).claimTask(anyString());

        mockMvc.perform(post("/api/workflow/tasks/NONEXIST/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40403"));
    }

    @Test
    void claimTask_alreadyClaimed_returnsBizError() throws Exception {
        doThrow(new BizException("WF-40904", "任务已被签收"))
            .when(taskOperationService).claimTask(anyString());

        mockMvc.perform(post("/api/workflow/tasks/T_001/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40904"));
    }

    @Test
    void approveTask_notAssignee_returnsBizError() throws Exception {
        doThrow(new BizException("WF-40903", "非任务办理人"))
            .when(taskOperationService).approveTask(anyString(), any(ApproveReqDTO.class));

        ApproveReqDTO req = new ApproveReqDTO();
        req.setOpinion("同意");

        mockMvc.perform(post("/api/workflow/tasks/T_001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40903"));
    }

    @Test
    void rejectTask_notAssignee_returnsBizError() throws Exception {
        doThrow(new BizException("WF-40903", "非任务办理人"))
            .when(taskOperationService).rejectTask(anyString(), any(RejectReqDTO.class));

        RejectReqDTO req = new RejectReqDTO();
        req.setOpinion("不符合要求");

        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40903"));
    }

    @Test
    void rejectTask_missingComment_returns400() throws Exception {
        // comment 为空，触发 @NotBlank 校验
        RejectReqDTO req = new RejectReqDTO();

        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferTask_missingToEmpId_returns400() throws Exception {
        // toEmpId 为空，触发 @NotBlank 校验
        TransferReqDTO req = new TransferReqDTO();
        req.setReason("出差交接");

        mockMvc.perform(post("/api/workflow/tasks/T_001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTaskDetail_taskNotFound_returnsBizError() throws Exception {
        when(todoQueryService.getTaskDetail(anyString(), anyString()))
                .thenThrow(new BizException("WF-40403", "任务不存在"));

        mockMvc.perform(get("/api/workflow/tasks/NONEXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40403"));
    }
}
