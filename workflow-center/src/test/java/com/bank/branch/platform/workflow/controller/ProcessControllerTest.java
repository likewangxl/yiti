package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    private ProcessStartService processStartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ProcessController(processStartService)).build();
    }

    @Test
    void getProcessByBusinessKey_shouldReturn200() throws Exception {
        // given
        BizProcessMapDTO dto = new BizProcessMapDTO();
        dto.setBusinessKey("LEAD:1001");
        dto.setProcessStatus("RUNNING");
        when(processStartService.getProcessByBusinessKey(anyString())).thenReturn(dto);

        // when & then
        mockMvc.perform(get("/api/workflow/processes/LEAD:1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.businessKey").value("LEAD:1001"));
    }

    @Test
    void getProcessByBizTypeAndBizId_shouldReturn200() throws Exception {
        // given
        BizProcessMapDTO dto = new BizProcessMapDTO();
        dto.setBizType("LEAD");
        dto.setBizId("1001");
        dto.setProcessStatus("RUNNING");
        when(processStartService.getProcessByBizTypeAndBizId(anyString(), anyString())).thenReturn(dto);

        // when & then
        mockMvc.perform(get("/api/workflow/processes/biz/LEAD/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.bizType").value("LEAD"))
                .andExpect(jsonPath("$.data.bizId").value("1001"));
    }
}
