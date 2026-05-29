package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.workflow.api.dto.flow.FlowDefDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowVariableDTO;
import com.bank.branch.platform.workflow.service.flow.FlowDefService;
import com.bank.branch.platform.workflow.service.flow.FlowPublishService;
import com.bank.branch.platform.workflow.service.flow.FlowVariableCatalog;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FlowDesignController 单元测试（standaloneSetup + Mockito mock，不启动 Spring 容器）。
 */
@ExtendWith(MockitoExtension.class)
class FlowDesignControllerTest {

    @Mock
    private FlowDefService flowDefService;

    @Mock
    private FlowPublishService flowPublishService;

    @Mock
    private FlowVariableCatalog flowVariableCatalog;

    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // standalone 模式：@BizAuth 拦截器不生效，CurrentUserApi mock 返回固定工号
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new FlowDesignController(flowDefService, flowPublishService, flowVariableCatalog, currentUserApi))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    // ── 1. GET /api/admin/workflow/flows ─────────────────────────────────

    @Test
    void list_returnsFlows() throws Exception {
        // given
        FlowDefDTO dto = new FlowDefDTO();
        dto.setFlowKey("alloc_adjust_corp");
        dto.setStatus("DRAFT");
        when(flowDefService.listAll()).thenReturn(List.of(dto));

        // when & then
        mockMvc.perform(get("/api/admin/workflow/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].flowKey").value("alloc_adjust_corp"));
    }

    // ── 2. GET /api/admin/workflow/flows/{id} ────────────────────────────

    @Test
    void getGraph_returnsGraph() throws Exception {
        // given
        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("测试流程");
        FlowNodeDTO node = new FlowNodeDTO();
        node.setNodeKey("start");
        node.setNodeType("START");
        graph.setNodes(List.of(node));
        when(flowDefService.getGraph("FD_001")).thenReturn(graph);

        // when & then
        mockMvc.perform(get("/api/admin/workflow/flows/FD_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.nodes[0].nodeKey").value("start"));
    }

    // ── 3. POST /api/admin/workflow/flows ────────────────────────────────

    @Test
    void create_returnsId() throws Exception {
        // given
        when(flowDefService.create(any(FlowGraphDTO.class), eq("admin"))).thenReturn("FD_NEW");

        FlowGraphDTO req = new FlowGraphDTO();
        req.setName("新流程");
        req.setBizType("ALLOC_ADJUST");

        // when & then
        mockMvc.perform(post("/api/admin/workflow/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("FD_NEW"));

        verify(flowDefService).create(any(FlowGraphDTO.class), eq("admin"));
    }

    // ── 4. PUT /api/admin/workflow/flows/{id} ────────────────────────────

    @Test
    void save_invokesService() throws Exception {
        // given
        doNothing().when(flowDefService).saveGraph(eq("FD_001"), any(FlowGraphDTO.class), eq("admin"));

        FlowGraphDTO req = new FlowGraphDTO();
        req.setName("更新后流程");

        // when & then
        mockMvc.perform(put("/api/admin/workflow/flows/FD_001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(flowDefService).saveGraph(eq("FD_001"), any(FlowGraphDTO.class), eq("admin"));
    }

    // ── 5. POST /api/admin/workflow/flows/{id}/publish ───────────────────

    @Test
    void publish_invokesService() throws Exception {
        // given
        doNothing().when(flowPublishService).publish(eq("FD_001"), anyString());

        // when & then
        mockMvc.perform(post("/api/admin/workflow/flows/FD_001/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(flowPublishService).publish(eq("FD_001"), anyString());
    }

    // ── 6. DELETE /api/admin/workflow/flows/{id} ─────────────────────────

    @Test
    void delete_invokesService() throws Exception {
        // given
        doNothing().when(flowDefService).deleteDraft("FD_001");

        // when & then
        mockMvc.perform(delete("/api/admin/workflow/flows/FD_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(flowDefService).deleteDraft("FD_001");
    }

    // ── 7. GET /api/admin/workflow/flows/meta/variables ──────────────────

    @Test
    void variables_returnsCatalog() throws Exception {
        // given
        FlowVariableDTO var = new FlowVariableDTO("bizKind", "业务种类", "string");
        when(flowVariableCatalog.variables("ALLOC_ADJUST")).thenReturn(List.of(var));

        // when & then
        mockMvc.perform(get("/api/admin/workflow/flows/meta/variables")
                        .param("bizType", "ALLOC_ADJUST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].field").value("bizKind"));
    }
}
