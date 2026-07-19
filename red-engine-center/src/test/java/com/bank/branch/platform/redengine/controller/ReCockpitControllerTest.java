package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.redengine.api.dto.ReAnnualGenerateReqDTO;
import com.bank.branch.platform.redengine.service.ReCockpitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReCockpitController 单元测试（standaloneSetup 惯例同 {@code ReOrgControllerTest}）。
 * <p>仅覆盖本次修复目标端点 {@code POST /api/re/cockpit/archive/generate/{year}}：
 * 高危操作补齐 {@code @AuditLog(reasonRequired = true)} + {@link ReAnnualGenerateReqDTO#reason}
 * {@code @NotBlank}，其余 7 个只读/执行端点行为未变更，不在本文件重复覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReCockpitControllerTest {

    @Mock
    private ReCockpitService reCockpitService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReCockpitController(reCockpitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void generateAnnualResult_withReason_shouldReturn200AndDelegateToService() throws Exception {
        ReAnnualGenerateReqDTO req = new ReAnnualGenerateReqDTO();
        req.setReason("年度考核期结束，按计划生成归档结果");
        doNothing().when(reCockpitService).generateAnnualResult(anyInt());

        mockMvc.perform(post("/api/re/cockpit/archive/generate/2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(reCockpitService).generateAnnualResult(2026);
    }

    @Test
    void generateAnnualResult_blankReason_shouldReturn400AndNotCallService() throws Exception {
        ReAnnualGenerateReqDTO req = new ReAnnualGenerateReqDTO();
        req.setReason("");

        mockMvc.perform(post("/api/re/cockpit/archive/generate/2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(reCockpitService, never()).generateAnnualResult(anyInt());
    }

    @Test
    void generateAnnualResult_missingBody_shouldReturn400AndNotCallService() throws Exception {
        mockMvc.perform(post("/api/re/cockpit/archive/generate/2026"))
                .andExpect(status().isBadRequest());

        verify(reCockpitService, never()).generateAnnualResult(anyInt());
    }
}
