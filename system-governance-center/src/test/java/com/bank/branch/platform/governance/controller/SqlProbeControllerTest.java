package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.service.SqlProbeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SqlProbeController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SqlProbeControllerTest {

    @Mock
    private SqlProbeService sqlProbeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SqlProbeController(sqlProbeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void executeSql_shouldReturn200() throws Exception {
        // given
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "test");
        when(sqlProbeService.executeSql(anyString(), anyString(), anyString()))
                .thenReturn(List.of(row));

        // when & then
        mockMvc.perform(post("/api/admin/sql-probe/execute")
                .param("sql", "SELECT * FROM sys_dict")
                .param("operatorEmpId", "emp001")
                .param("reason", "测试查询"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listHistory_shouldReturn200WithPageResult() throws Exception {
        // given
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId("AL_001");
        PageResult<AuditLogDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(sqlProbeService.listHistory(anyString(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sql-probe/history")
                .param("operatorEmpId", "emp001")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void executeSql_nonSelect_returnsBizError() throws Exception {
        when(sqlProbeService.executeSql(anyString(), anyString(), anyString()))
                .thenThrow(new BizException("GOV-42201", "非SELECT SQL语句"));

        mockMvc.perform(post("/api/admin/sql-probe/execute")
                .param("sql", "DELETE FROM sys_dict")
                .param("operatorEmpId", "emp001")
                .param("reason", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-42201"));
    }

    @Test
    void executeSql_concurrencyExceeded_returnsBizError() throws Exception {
        when(sqlProbeService.executeSql(anyString(), anyString(), anyString()))
                .thenThrow(new BizException("GOV-42202", "SQL并发数超限"));

        mockMvc.perform(post("/api/admin/sql-probe/execute")
                .param("sql", "SELECT 1")
                .param("operatorEmpId", "emp001")
                .param("reason", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-42202"));
    }
}
