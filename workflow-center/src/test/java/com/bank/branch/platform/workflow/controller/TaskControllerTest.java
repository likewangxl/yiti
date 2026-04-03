package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.PageResult;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
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

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TaskController(todoQueryService, taskOperationService)).build();
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
        mockMvc.perform(get("/api/workflow/tasks/todo")
                        .param("empId", "EMP001")
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
        mockMvc.perform(get("/api/workflow/tasks/done")
                        .param("empId", "EMP001"))
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
        mockMvc.perform(get("/api/workflow/tasks/T_003")
                        .param("empId", "EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskInfo.taskId").value("T_003"));
    }

    @Test
    void claimTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).claimTask(anyString(), anyString());

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/claim")
                        .param("empId", "EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void approveTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).approveTask(anyString(), anyString(), any(), any());

        ApproveReqDTO req = new ApproveReqDTO();
        req.setComment("同意");
        req.setVariables(Map.of("approved", true));

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/approve")
                        .param("empId", "EMP001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void rejectTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).rejectTask(anyString(), anyString(), anyString());

        RejectReqDTO req = new RejectReqDTO();
        req.setComment("不符合要求");

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .param("empId", "EMP001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void transferTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(taskOperationService).transferTask(anyString(), anyString(), anyString(), anyString());

        TransferReqDTO req = new TransferReqDTO();
        req.setToEmpId("EMP002");
        req.setReason("出差交接");

        // when & then
        mockMvc.perform(post("/api/workflow/tasks/T_001/transfer")
                        .param("empId", "EMP001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
