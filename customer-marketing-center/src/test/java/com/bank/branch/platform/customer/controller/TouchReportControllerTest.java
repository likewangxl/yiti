package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.dto.resp.TouchStatisticVO;
import com.bank.branch.platform.customer.service.TouchReportService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TouchReportController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest 基类，通过 @MockBean TouchReportService 替换真实业务逻辑。
 * 验证三个端点：触达报告列表、触达统计、导出。
 * </p>
 */
class TouchReportControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TouchReportService touchReportService;

    // ==================== GET /api/touch-reports ====================

    /**
     * 不带过滤条件时，正常返回分页列表，HTTP 200，响应结构正确。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void listPage_shouldReturn200WithPagedResult() throws Exception {
        // given
        TouchReportVO vo = new TouchReportVO();
        vo.setTaskNo("TK-20260101-001");
        vo.setTaskType("FIRST_TOUCH");
        vo.setTaskStatus("PENDING");
        vo.setSlaStatus("GREEN");
        vo.setCustName("深圳科技有限公司");
        vo.setAssigneeEmpId("E10001");
        vo.setOrgId("ORG_SZ_001");
        vo.setPlanFinishTime(LocalDateTime.of(2026, 1, 10, 18, 0));
        vo.setLogCount(2L);

        PageResult<TouchReportVO> page = PageResult.of(1, 20, 1L, Collections.singletonList(vo));
        when(touchReportService.listPage(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        // when/then
        mockMvc.perform(get("/api/touch-reports")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].taskNo").value("TK-20260101-001"))
                .andExpect(jsonPath("$.page.records[0].custName").value("深圳科技有限公司"))
                .andExpect(jsonPath("$.page.records[0].logCount").value(2));
    }

    /**
     * 带 keyword 和 status 过滤参数时，参数应正确透传给 Service。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void listPage_shouldPassFilterParamsToService() throws Exception {
        // given
        PageResult<TouchReportVO> emptyPage = PageResult.of(1, 20, 0L, Collections.emptyList());
        when(touchReportService.listPage(eq("科技"), eq("PENDING"), isNull(), eq(1), eq(20)))
                .thenReturn(emptyPage);

        // when/then
        mockMvc.perform(get("/api/touch-reports")
                        .param("keyword", "科技")
                        .param("status", "PENDING")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(0));

        verify(touchReportService).listPage("科技", "PENDING", null, 1, 20);
    }

    // ==================== GET /api/touch-reports/statistics ====================

    /**
     * 统计端点：正常返回状态分组统计，HTTP 200，响应结构正确。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void statistics_shouldReturn200WithStatList() throws Exception {
        // given
        TouchStatisticVO stat1 = new TouchStatisticVO();
        stat1.setStatus("PENDING");
        stat1.setCount(15L);

        TouchStatisticVO stat2 = new TouchStatisticVO();
        stat2.setStatus("SUCCESS");
        stat2.setCount(8L);

        List<TouchStatisticVO> stats = Arrays.asList(stat1, stat2);
        when(touchReportService.statistic(isNull())).thenReturn(stats);

        // when/then
        mockMvc.perform(get("/api/touch-reports/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].count").value(15))
                .andExpect(jsonPath("$.data[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[1].count").value(8));
    }

    /**
     * 统计端点：带 orgId 查询参数时，正确透传给 Service。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void statistics_shouldPassOrgIdToService() throws Exception {
        // given
        when(touchReportService.statistic(eq("ORG_SZ_001"))).thenReturn(Collections.emptyList());

        // when/then
        mockMvc.perform(get("/api/touch-reports/statistics")
                        .param("orgId", "ORG_SZ_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(touchReportService).statistic("ORG_SZ_001");
    }

    // ==================== GET /api/touch-reports/export ====================
    // 详细导出端点测试见 TouchReportExportControllerTest（TDD Red-Green 闭环）

    /**
     * 导出端点：返回 HTTP 200，Content-Type 为 text/csv（真实 CSV 导出实现）。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void export_shouldReturn200WithCsvContentType() throws Exception {
        when(touchReportService.listAllForExport(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(10000)))
                .thenReturn(java.util.Collections.emptyList());

        // when/then: 导出接口返回 HTTP 200 且 Content-Type 为 text/csv
        mockMvc.perform(get("/api/touch-reports/export"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type",
                                org.hamcrest.Matchers.startsWith("text/csv")));
    }
}
