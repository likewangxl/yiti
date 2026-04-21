package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.service.TouchReportService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TouchReportController 导出端点集成测试（TDD Red 阶段）。
 * <p>
 * 覆盖 GET /api/touch-reports/export 端点的真实 CSV 导出实现：
 * - Content-Type = text/csv
 * - Content-Disposition = attachment
 * - 响应 body 首行为 CSV 表头
 * - 数据行为 CSV 格式
 * - @AuditLog(reasonRequired=true) 注解存在性验证
 * </p>
 */
class TouchReportExportControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TouchReportService touchReportService;

    /**
     * 触达报告导出：Content-Type 应为 text/csv。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTouchReports_contentTypeShouldBeCsv() throws Exception {
        when(touchReportService.listAllForExport(isNull(), isNull(), isNull(), eq(10000)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/touch-reports/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.startsWith("text/csv")));
    }

    /**
     * 触达报告导出：Content-Disposition 应含 attachment。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTouchReports_contentDispositionShouldContainAttachment() throws Exception {
        when(touchReportService.listAllForExport(isNull(), isNull(), isNull(), eq(10000)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/touch-reports/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")));
    }

    /**
     * 触达报告导出：响应 body 首行应为 CSV 表头。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTouchReports_firstLineShouldBeCsvHeader() throws Exception {
        when(touchReportService.listAllForExport(isNull(), isNull(), isNull(), eq(10000)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/touch-reports/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("任务编号,任务类型")));
    }

    /**
     * 触达报告导出：有数据时响应 body 应包含 CSV 数据行。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTouchReports_returnsCsvDataRows() throws Exception {
        TouchReportVO vo1 = new TouchReportVO();
        vo1.setTaskNo("TK-20260101-001");
        vo1.setTaskType("FIRST_TOUCH");
        vo1.setTaskStatus("PENDING");
        vo1.setSlaStatus("GREEN");
        vo1.setCustName("深圳科技有限公司");
        vo1.setAssigneeEmpId("E10001");
        vo1.setOrgId("ORG_SZ_001");
        vo1.setPlanFinishTime(LocalDateTime.of(2026, 1, 10, 18, 0));
        vo1.setLogCount(2L);

        TouchReportVO vo2 = new TouchReportVO();
        vo2.setTaskNo("TK-20260101-002");
        vo2.setTaskType("FOLLOW_UP");
        vo2.setTaskStatus("SUCCESS");
        vo2.setSlaStatus("GREEN");
        vo2.setCustName("广州贸易股份有限公司");
        vo2.setAssigneeEmpId("E10002");
        vo2.setOrgId("ORG_SZ_001");
        vo2.setLogCount(5L);

        when(touchReportService.listAllForExport(isNull(), isNull(), isNull(), eq(10000)))
                .thenReturn(Arrays.asList(vo1, vo2));

        mockMvc.perform(get("/api/touch-reports/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("TK-20260101-001")))
                .andExpect(content().string(Matchers.containsString("深圳科技有限公司")))
                .andExpect(content().string(Matchers.containsString("TK-20260101-002")));
    }

    /**
     * 触达报告导出：@AuditLog reasonRequired=true 注解存在性验证。
     */
    @Test
    void exportTouchReports_shouldHaveAuditLogAnnotationWithReasonRequired() throws Exception {
        Class<?> controllerClass = TouchReportController.class;
        Method exportMethod = null;
        for (Method m : controllerClass.getDeclaredMethods()) {
            if (m.getName().equals("export")) {
                exportMethod = m;
                break;
            }
        }
        assertThat(exportMethod).isNotNull();

        com.bank.branch.platform.common.aop.annotation.AuditLog auditLog =
                exportMethod.getAnnotation(com.bank.branch.platform.common.aop.annotation.AuditLog.class);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.reasonRequired()).isTrue();
        assertThat(auditLog.resourceType()).isEqualTo("TOUCH_REPORT");
    }
}
