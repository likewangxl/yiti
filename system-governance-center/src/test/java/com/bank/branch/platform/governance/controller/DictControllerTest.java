package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.DictCreateReqDTO;
import com.bank.branch.platform.governance.api.dto.DictItemRespDTO;
import com.bank.branch.platform.governance.api.dto.DictUpdateReqDTO;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.service.DictService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DictController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DictControllerTest {

    @Mock
    private DictService dictService;

    private MockMvc mockMvc;
    private MockMvc adminMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 公共查询接口测试
        mockMvc = MockMvcBuilders.standaloneSetup(new DictController(dictService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        // 管理接口测试
        adminMockMvc = MockMvcBuilders.standaloneSetup(new AdminDictController(dictService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getDictItems_shouldReturn200WithList() throws Exception {
        // given
        SysDict dict = new SysDict();
        dict.setId("D_001");
        dict.setDictType("INDUSTRY");
        dict.setDictCode("IT");
        dict.setDictLabel("信息技术");
        dict.setDictValue("IT");
        dict.setStatus("ACTIVE");
        when(dictService.getDictItems(anyString())).thenReturn(List.of(dict));

        // when & then
        mockMvc.perform(get("/api/sys/dicts/INDUSTRY/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createDict_shouldReturn200() throws Exception {
        // given
        SysDict dict = new SysDict();
        dict.setId("D_NEW");
        dict.setDictType("INDUSTRY");
        dict.setDictCode("FIN");
        dict.setDictLabel("金融");
        dict.setDictValue("FIN");
        dict.setStatus("ACTIVE");
        when(dictService.createDict(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(dict);

        DictCreateReqDTO req = new DictCreateReqDTO();
        req.setDictType("INDUSTRY");
        req.setDictCode("FIN");
        req.setDictLabel("金融");
        req.setDictValue("FIN");
        req.setSortOrder(1);

        // when & then
        adminMockMvc.perform(post("/api/admin/sys/dicts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void updateDict_shouldReturn200() throws Exception {
        // given
        SysDict dict = new SysDict();
        dict.setId("D_001");
        dict.setDictType("INDUSTRY");
        dict.setDictCode("IT");
        dict.setDictLabel("信息技术更新");
        dict.setDictValue("IT");
        dict.setStatus("ACTIVE");
        when(dictService.updateDict(anyString(), any(), any(), any(), any())).thenReturn(dict);

        DictUpdateReqDTO req = new DictUpdateReqDTO();
        req.setDictLabel("信息技术更新");

        // when & then
        adminMockMvc.perform(put("/api/admin/sys/dicts/D_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void deleteDict_shouldReturn200() throws Exception {
        // given
        doNothing().when(dictService).deleteDict(anyString());

        // when & then
        adminMockMvc.perform(delete("/api/admin/sys/dicts/D_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void createDict_duplicateCode_returnsBizError() throws Exception {
        when(dictService.createDict(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new BizException("GOV-40901", "字典编码重复"));

        DictCreateReqDTO req = new DictCreateReqDTO();
        req.setDictType("INDUSTRY");
        req.setDictCode("IT");
        req.setDictLabel("信息技术");
        req.setDictValue("IT");

        adminMockMvc.perform(post("/api/admin/sys/dicts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40901"));
    }

    @Test
    void createDict_missingRequired_returns400() throws Exception {
        // dictType 为空，触发 @NotBlank 校验
        DictCreateReqDTO req = new DictCreateReqDTO();
        req.setDictCode("IT");
        req.setDictLabel("信息技术");
        req.setDictValue("IT");

        adminMockMvc.perform(post("/api/admin/sys/dicts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDict_invalidDictTypePattern_returns400() throws Exception {
        // dictType 不符合 ^[A-Z_]+$ 正则
        DictCreateReqDTO req = new DictCreateReqDTO();
        req.setDictType("lower_case");
        req.setDictCode("IT");
        req.setDictLabel("信息技术");
        req.setDictValue("IT");

        adminMockMvc.perform(post("/api/admin/sys/dicts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDict_notFound_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40001", "字典类型不存在"))
            .when(dictService).updateDict(anyString(), any(), any(), any(), any());

        DictUpdateReqDTO req = new DictUpdateReqDTO();
        req.setDictLabel("新标签");

        adminMockMvc.perform(put("/api/admin/sys/dicts/NOT_EXIST")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40001"));
    }

    @Test
    void deleteDict_notFound_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40001", "字典类型不存在"))
            .when(dictService).deleteDict(anyString());

        adminMockMvc.perform(delete("/api/admin/sys/dicts/NOT_EXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40001"));
    }
}
