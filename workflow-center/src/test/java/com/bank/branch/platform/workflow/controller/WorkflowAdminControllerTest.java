package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.service.WorkflowAdminService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WorkflowAdminController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class WorkflowAdminControllerTest {

    @Mock
    private WorkflowAdminService workflowAdminService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WorkflowAdminController(workflowAdminService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listCandidateConfigs_shouldReturn200() throws Exception {
        // given
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setId("CC_001");
        conf.setProcessDefinitionKey("lead_approval");
        conf.setNodeKey("dept_review");
        conf.setCandidateType("ROLE");
        conf.setCandidateValue("[\"BRANCH_HEAD\"]");
        when(workflowAdminService.listCandidateConfigs(anyString())).thenReturn(List.of(conf));

        // when & then
        mockMvc.perform(get("/api/admin/workflow/candidate-configs")
                        .param("processDefinitionKey", "lead_approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("CC_001"));
    }

    @Test
    void saveCandidateConfig_shouldReturn200() throws Exception {
        // given
        doNothing().when(workflowAdminService).saveCandidateConfig(any(WfNodeCandidateConf.class));

        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setProcessDefinitionKey("lead_approval");
        conf.setNodeKey("dept_review");
        conf.setCandidateType("ROLE");
        conf.setCandidateValue("[\"BRANCH_HEAD\"]");

        // when & then
        mockMvc.perform(post("/api/admin/workflow/candidate-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void listTimeoutRules_shouldReturn200() throws Exception {
        // given
        WfTimeoutRule rule = new WfTimeoutRule();
        rule.setId("TR_001");
        rule.setProcessDefinitionKey("lead_approval");
        rule.setNodeKey("dept_review");
        rule.setTimeoutHours(48);
        rule.setWarningHours(24);
        when(workflowAdminService.listTimeoutRules(anyString())).thenReturn(List.of(rule));

        // when & then
        mockMvc.perform(get("/api/admin/workflow/timeout-rules")
                        .param("processDefinitionKey", "lead_approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("TR_001"));
    }

    @Test
    void saveTimeoutRule_shouldReturn200() throws Exception {
        // given
        doNothing().when(workflowAdminService).saveTimeoutRule(any(WfTimeoutRule.class));

        WfTimeoutRule rule = new WfTimeoutRule();
        rule.setProcessDefinitionKey("lead_approval");
        rule.setNodeKey("dept_review");
        rule.setTimeoutHours(48);
        rule.setWarningHours(24);

        // when & then
        mockMvc.perform(post("/api/admin/workflow/timeout-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rule)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void saveCandidateConfig_malformedJson_returns500() throws Exception {
        // GlobalExceptionHandler 未处理 HttpMessageNotReadableException，兜底返回 500
        mockMvc.perform(post("/api/admin/workflow/candidate-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json}"))
                .andExpect(status().isInternalServerError());
    }
}
