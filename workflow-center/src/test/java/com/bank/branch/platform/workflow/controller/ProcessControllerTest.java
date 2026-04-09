package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
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
        // given - 创建 ProcessInstanceInfo
        ProcessQueryService.ProcessInstanceInfo info = new ProcessQueryService.ProcessInstanceInfo();
        info.setProcessInstanceId("PID_001");
        info.setProcessDefinitionKey("loanApproval");
        info.setProcessDefinitionName("贷款审批流程");
        info.setProcessDefinitionVersion(1);
        info.setBusinessKey("LOAN:1001");
        info.setStartUserId("E001");
        info.setCurrentActivityId("task_1");
        info.setIsEnded(false);
        info.setStartTime(LocalDateTime.of(2026, 4, 1, 10, 0, 0));
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
        // given - 创建 ApprovalLogDTO 列表
        ApprovalLogDTO dto1 = new ApprovalLogDTO();
        dto1.setNodeKey("task_1");
        dto1.setNodeName("提交申请");
        dto1.setOperator("E001");
        dto1.setOperatorName("张三");
        dto1.setAction("SUBMIT");
        dto1.setOperateTime(LocalDateTime.of(2026, 4, 1, 10, 0, 0));

        ApprovalLogDTO dto2 = new ApprovalLogDTO();
        dto2.setNodeKey("task_2");
        dto2.setNodeName("审批");
        dto2.setOperator("E002");
        dto2.setOperatorName("李四");
        dto2.setAction("APPROVE");
        dto2.setOperateTime(LocalDateTime.of(2026, 4, 1, 10, 30, 0));

        when(processQueryService.getProcessHistory(anyString())).thenReturn(List.of(dto1, dto2));

        // when & then
        mockMvc.perform(get("/api/workflow/processes/PID_001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].nodeKey").value("task_1"))
                .andExpect(jsonPath("$.data[0].nodeName").value("提交申请"))
                .andExpect(jsonPath("$.data[0].operator").value("E001"))
                .andExpect(jsonPath("$.data[1].nodeKey").value("task_2"))
                .andExpect(jsonPath("$.data[1].nodeName").value("审批"));
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
