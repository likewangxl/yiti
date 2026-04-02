package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.ConfigDTO;
import com.bank.branch.platform.governance.api.dto.ConfigUpdateReqDTO;
import com.bank.branch.platform.governance.service.ConfigService;
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
 * ConfigController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    @Mock
    private ConfigService configService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConfigController(configService)).build();
    }

    @Test
    void listConfigs_shouldReturn200WithPageResult() throws Exception {
        // given
        ConfigDTO dto = new ConfigDTO();
        dto.setConfigKey("sys.title");
        PageResult<ConfigDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(configService.listConfigs(any(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sys/configs")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void updateConfig_shouldReturn200() throws Exception {
        // given
        doNothing().when(configService).updateConfig(anyString(), anyString());

        ConfigUpdateReqDTO req = new ConfigUpdateReqDTO();
        req.setConfigValue("新标题");

        // when & then
        mockMvc.perform(put("/api/admin/sys/configs/sys.title")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
