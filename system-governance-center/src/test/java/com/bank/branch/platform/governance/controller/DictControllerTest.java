package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.DictCreateReqDTO;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DictController(dictService))
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
        when(dictService.createDict(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(dict);

        DictCreateReqDTO req = new DictCreateReqDTO();
        req.setDictType("INDUSTRY");
        req.setDictCode("FIN");
        req.setDictLabel("金融");
        req.setDictValue("FIN");
        req.setSortOrder(1);

        // when & then
        mockMvc.perform(post("/api/admin/sys/dicts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void listByPage_shouldReturn200WithPageResult() throws Exception {
        // given
        SysDict dict = new SysDict();
        dict.setId("D_001");
        PageResult<SysDict> pageResult = PageResult.of(1, 20, 1L, List.of(dict));
        when(dictService.listByPage(any(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sys/dicts")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void deleteDict_shouldReturn200() throws Exception {
        // given
        doNothing().when(dictService).deleteDict(anyString());

        // when & then
        mockMvc.perform(delete("/api/admin/sys/dicts/D_001"))
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

        mockMvc.perform(post("/api/admin/sys/dicts")
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

        mockMvc.perform(post("/api/admin/sys/dicts")
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

        mockMvc.perform(post("/api/admin/sys/dicts")
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

        mockMvc.perform(put("/api/admin/sys/dicts/NOT_EXIST")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40001"));
    }

    @Test
    void deleteDict_notFound_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40001", "字典类型不存在"))
            .when(dictService).deleteDict(anyString());

        mockMvc.perform(delete("/api/admin/sys/dicts/NOT_EXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40001"));
    }
}
