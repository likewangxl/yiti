package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.BizScopeRespDTO;
import com.bank.branch.platform.auth.api.dto.BizScopeSaveReqDTO;
import com.bank.branch.platform.auth.service.BizScopeService;
import com.bank.branch.platform.common.web.PageResult;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BizScopeController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BizScopeControllerTest {

    @Mock
    private BizScopeService bizScopeService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BizScopeController(bizScopeService)).build();
    }

    @Test
    void listBizScopes_shouldReturn200WithPageResult() throws Exception {
        // given
        BizScopeRespDTO dto = new BizScopeRespDTO();
        dto.setId("S_001");
        dto.setRoleId("R_001");
        PageResult<BizScopeRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(bizScopeService.listByPage(any(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/biz-scopes/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void saveBizScope_shouldReturn200WithSavedDto() throws Exception {
        // given
        BizScopeRespDTO dto = new BizScopeRespDTO();
        dto.setId("S_NEW");
        dto.setBizType("CUSTOMER");
        dto.setDataScope("ALL");
        when(bizScopeService.saveBizScope(anyString(), anyString(), anyString(), anyString())).thenReturn(dto);

        BizScopeSaveReqDTO req = new BizScopeSaveReqDTO();
        req.setRoleId("R_001");
        req.setBizType("CUSTOMER");
        req.setDataScope("ALL");
        req.setReason("初始配置");

        // when & then
        mockMvc.perform(post("/api/admin/biz-scopes/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.bizType").value("CUSTOMER"));
    }

    @Test
    void deleteBizScope_shouldReturn200() throws Exception {
        // given
        doNothing().when(bizScopeService).deleteBizScope(anyString(), anyString());

        // when & then
        mockMvc.perform(delete("/api/admin/biz-scopes/S_001")
                .param("reason", "配置变更"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
