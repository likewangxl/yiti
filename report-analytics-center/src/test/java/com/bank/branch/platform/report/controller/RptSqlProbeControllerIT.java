package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.dto.resp.SchemaWhitelistRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExecuteRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeHistoryRespDTO;
import com.bank.branch.platform.report.service.SqlProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SQL 探查 D.1-D.4 端到端 IT（Task M4.3.1 / M4.3.2 / M4.3.3 + M4.2 Controller 透传守护）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>D.1 POST /sql-probe/execute：必填 remark + 透传 service 返回</li>
 *   <li>D.2 GET  /sql-probe/history：分页（page.total + records）</li>
 *   <li>D.3 GET  /sql-probe/history/{id}：详情</li>
 *   <li>D.4 GET  /sql-probe/schema-whitelist：白名单展示</li>
 * </ol>
 *
 * <p>本 IT mock 整个 SqlProbeService（避免触发真实 DataSource 连接），
 * 仅守护 Controller 路由 + JSON 序列化 + @BizAuth 标注（与 BaseControllerIT 的 @MockBean
 * AuthorizationInterceptor 配合放行）.
 */
class RptSqlProbeControllerIT extends BaseControllerIT {

    @MockBean
    private SqlProbeService sqlProbeService;

    @Test
    void execute_returns200_withTaskId() throws Exception {
        when(sqlProbeService.execute(any())).thenReturn(SqlProbeExecuteRespDTO.builder()
                .historyId("HIST123")
                .columns(List.of("id"))
                .rows(List.of(Map.of("id", 1)))
                .rowCount(1)
                .executionTimeMs(42)
                .build());

        String body = """
                { "sql": "SELECT id FROM CUST_MASTER", "remark": "诊断" }
                """;
        mvc.perform(post("/api/reports/sql-probe/execute")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.historyId").value("HIST123"))
                .andExpect(jsonPath("$.data.rowCount").value(1));
    }

    @Test
    void execute_missingRemark_returns400() throws Exception {
        // remark 字段缺失：@NotBlank 触发 400
        String body = """
                { "sql": "SELECT id FROM CUST_MASTER" }
                """;
        mvc.perform(post("/api/reports/sql-probe/execute")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void queryHistory_returnsPaged() throws Exception {
        SqlProbeHistoryRespDTO rec = SqlProbeHistoryRespDTO.builder()
                .id("H001").empId("E_TECH001").status("SUCCESS")
                .sqlText("SELECT 1").remark("test")
                .createdTime(LocalDateTime.now()).build();
        PageResult<SqlProbeHistoryRespDTO> page =
                PageResult.of(1, 20, 1L, List.of(rec));
        when(sqlProbeService.queryHistory(any())).thenReturn(page);

        mvc.perform(get("/api/reports/sql-probe/history")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].id").value("H001"))
                .andExpect(jsonPath("$.page.records[0].status").value("SUCCESS"));
    }

    @Test
    void getHistoryDetail_returns200() throws Exception {
        SqlProbeHistoryRespDTO d = SqlProbeHistoryRespDTO.builder()
                .id("H001").empId("E_TECH001").status("SUCCESS")
                .sqlText("SELECT 1 FROM CUST_MASTER").remark("test")
                .rowCount(1).executionTimeMs(50).build();
        when(sqlProbeService.getHistoryDetail(anyString())).thenReturn(d);

        mvc.perform(get("/api/reports/sql-probe/history/H001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("H001"))
                .andExpect(jsonPath("$.data.executionTimeMs").value(50));
    }

    @Test
    void getSchemaWhitelist_returns200_withConfigs() throws Exception {
        when(sqlProbeService.getSchemaWhitelist()).thenReturn(
                SchemaWhitelistRespDTO.builder()
                        .tables(List.of("cust_master", "kpi_result"))
                        .forbiddenKeywords(List.of("DROP", "DELETE"))
                        .maxRows(1000)
                        .maxSqlLength(5000)
                        .maxSubqueryDepth(3)
                        .build());

        mvc.perform(get("/api/reports/sql-probe/schema-whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tables[0]").value("cust_master"))
                .andExpect(jsonPath("$.data.maxRows").value(1000))
                .andExpect(jsonPath("$.data.maxSubqueryDepth").value(3));
    }
}
