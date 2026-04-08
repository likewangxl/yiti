package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.SqlProbeReqDTO;
import com.bank.branch.platform.governance.api.dto.SqlProbeRespDTO;
import com.bank.branch.platform.governance.service.SqlProbeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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

        // 设置 DataScopeContext（模拟当前登录用户）
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("emp001");
        DataScopeContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void executeSql_shouldReturn200() throws Exception {
        // given
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "test");
        SqlProbeRespDTO resp = new SqlProbeRespDTO();
        resp.setColumns(List.of("id", "name"));
        resp.setRows(List.of(row));
        resp.setRowCount(1);
        resp.setExecutionTime(35);
        when(sqlProbeService.executeSql(any(SqlProbeReqDTO.class), eq("emp001")))
                .thenReturn(resp);

        // when & then
        mockMvc.perform(post("/api/admin/sql-probe/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\": \"SELECT * FROM sys_dict\", \"remark\": \"测试查询\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.columns").isArray());
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
        when(sqlProbeService.executeSql(any(SqlProbeReqDTO.class), eq("emp001")))
                .thenThrow(new BizException("GOV-42201", "非SELECT SQL语句"));

        mockMvc.perform(post("/api/admin/sql-probe/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\": \"DELETE FROM sys_dict\", \"remark\": \"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-42201"));
    }

    @Test
    void executeSql_concurrencyExceeded_returnsBizError() throws Exception {
        when(sqlProbeService.executeSql(any(SqlProbeReqDTO.class), eq("emp001")))
                .thenThrow(new BizException("GOV-42202", "SQL并发数超限"));

        mockMvc.perform(post("/api/admin/sql-probe/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\": \"SELECT 1\", \"remark\": \"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-42202"));
    }
}
