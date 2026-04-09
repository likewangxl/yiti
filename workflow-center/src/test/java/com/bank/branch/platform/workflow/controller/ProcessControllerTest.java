package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.service.ProcessQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProcessController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ProcessControllerTest {

    @Mock
    private ProcessQueryService processQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ProcessController(processQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== C.1 流程实例查询 ====================

    @Test
    void getProcessInstanceInfo_shouldReturn200() throws Exception {
        // given - 创建 ProcessInstanceInfo record
        ProcessQueryService.ProcessInstanceInfo info = new ProcessQueryService.ProcessInstanceInfo(
                "PID_001",
                "loanApproval",
                "贷款审批流程",
                1,
                "LOAN:1001",
                "E001",
                "task_1",
                false,
                LocalDateTime.of(2026, 4, 1, 10, 0, 0),
                null,
                null
        );
        when(processQueryService.getProcessInstanceInfo(anyString())).thenReturn(info);

        // when & then
        mockMvc.perform(get("/api/workflow/processes/PID_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processInstanceId").value("PID_001"))
                .andExpect(jsonPath("$.data.processDefinitionKey").value("loanApproval"))
                .andExpect(jsonPath("$.data.businessKey").value("LOAN:1001"))
                .andExpect(jsonPath("$.data.isEnded").value(false));
    }

    @Test
    void getProcessInstanceInfo_notFound_returnsBizError() throws Exception {
        when(processQueryService.getProcessInstanceInfo(anyString()))
                .thenThrow(new BizException("WF-40402", "流程实例不存在"));

        mockMvc.perform(get("/api/workflow/processes/NONEXISTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40402"))
                .andExpect(jsonPath("$.message").value("流程实例不存在"));
    }

    // ==================== C.2 流程图查询 ====================

    @Test
    void getProcessDiagram_shouldReturn200() throws Exception {
        // given - 返回 PNG 图像字节
        byte[] pngData = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG 文件头
        when(processQueryService.generateProcessDiagram(anyString())).thenReturn(pngData);

        // when & then
        mockMvc.perform(get("/api/workflow/processes/PID_001/diagram"))
                .andExpect(status().isOk());
    }

    @Test
    void getProcessDiagram_notFound_returnsBizError() throws Exception {
        when(processQueryService.generateProcessDiagram(anyString()))
                .thenThrow(new BizException("WF-40402", "流程实例不存在"));

        mockMvc.perform(get("/api/workflow/processes/NONEXISTENT/diagram"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40402"));
    }

    // ==================== C.3 流程历史查询 ====================

    @Test
    void getProcessHistory_shouldReturn200() throws Exception {
        // given - 创建 ProcessHistoryDTO 列表
        ProcessQueryService.ProcessHistoryDTO dto1 = new ProcessQueryService.ProcessHistoryDTO();
        dto1.setActivityId("task_1");
        dto1.setActivityName("提交申请");
        dto1.setActivityType("userTask");
        dto1.setAssignee("E001");
        dto1.setStartTime(LocalDateTime.of(2026, 4, 1, 10, 0, 0));
        dto1.setEndTime(LocalDateTime.of(2026, 4, 1, 10, 30, 0));
        dto1.setDurationMs(1800000L);

        ProcessQueryService.ProcessHistoryDTO dto2 = new ProcessQueryService.ProcessHistoryDTO();
        dto2.setActivityId("task_2");
        dto2.setActivityName("审批");
        dto2.setActivityType("userTask");
        dto2.setAssignee("E002");
        dto2.setStartTime(LocalDateTime.of(2026, 4, 1, 10, 30, 0));
        dto2.setEndTime(LocalDateTime.of(2026, 4, 1, 11, 0, 0));
        dto2.setDurationMs(1800000L);

        when(processQueryService.getProcessHistory(anyString())).thenReturn(List.of(dto1, dto2));

        // when & then
        mockMvc.perform(get("/api/workflow/processes/PID_001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].activityId").value("task_1"))
                .andExpect(jsonPath("$.data[0].activityName").value("提交申请"))
                .andExpect(jsonPath("$.data[1].activityId").value("task_2"))
                .andExpect(jsonPath("$.data[1].activityName").value("审批"));
    }

    @Test
    void getProcessHistory_notFound_returnsBizError() throws Exception {
        when(processQueryService.getProcessHistory(anyString()))
                .thenThrow(new BizException("WF-40402", "流程实例不存在"));

        mockMvc.perform(get("/api/workflow/processes/NONEXISTENT/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40402"));
    }
}
