package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.service.TagCustomerService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TagCustomerController 导出端点集成测试（TDD Red 阶段）。
 * <p>
 * 覆盖 GET /api/tags/{tagId}/customers/export 端点：
 * - Content-Type = text/csv
 * - Content-Disposition = attachment
 * - 响应 body 首行为 CSV 表头
 * - 数据行为 CSV 格式
 * - @AuditLog(reasonRequired=true) 注解存在性验证
 * </p>
 */
class TagCustomerExportControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TagCustomerService tagCustomerService;

    /**
     * 标签客户导出：Content-Type 应为 text/csv。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTagCustomers_contentTypeShouldBeCsv() throws Exception {
        when(tagCustomerService.listCustomersForExport(eq("tag-001")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tags/tag-001/customers/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.startsWith("text/csv")));
    }

    /**
     * 标签客户导出：Content-Disposition 应含 attachment。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTagCustomers_contentDispositionShouldContainAttachment() throws Exception {
        when(tagCustomerService.listCustomersForExport(eq("tag-001")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tags/tag-001/customers/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")));
    }

    /**
     * 标签客户导出：响应 body 首行应为 CSV 表头。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTagCustomers_firstLineShouldBeCsvHeader() throws Exception {
        when(tagCustomerService.listCustomersForExport(eq("tag-001")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tags/tag-001/customers/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("客户编号,客户名称")));
    }

    /**
     * 标签客户导出：有数据时响应 body 应包含 CSV 数据行。
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void exportTagCustomers_returnsCsvDataRows() throws Exception {
        CustMaster c1 = new CustMaster();
        c1.setCustNo("CUST_00001");
        c1.setCustName("深圳科技有限公司");
        c1.setUnifiedCreditCode("91440300XXXXXXXXX1");
        c1.setStatus("ACTIVE");
        c1.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        CustMaster c2 = new CustMaster();
        c2.setCustNo("CUST_00002");
        c2.setCustName("广州贸易股份有限公司");
        c2.setUnifiedCreditCode("91440100XXXXXXXXX2");
        c2.setStatus("INACTIVE");
        c2.setCreatedTime(LocalDateTime.of(2026, 1, 2, 0, 0));

        when(tagCustomerService.listCustomersForExport(eq("tag-001")))
                .thenReturn(Arrays.asList(c1, c2));

        mockMvc.perform(get("/api/tags/tag-001/customers/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("CUST_00001")))
                .andExpect(content().string(Matchers.containsString("深圳科技有限公司")))
                .andExpect(content().string(Matchers.containsString("CUST_00002")));
    }

    /**
     * 标签客户导出：@AuditLog reasonRequired=true 注解存在性验证。
     */
    @Test
    void exportTagCustomers_shouldHaveAuditLogAnnotationWithReasonRequired() throws Exception {
        Class<?> controllerClass = TagCustomerController.class;
        Method exportMethod = null;
        for (Method m : controllerClass.getDeclaredMethods()) {
            if (m.getName().equals("exportTagCustomers")) {
                exportMethod = m;
                break;
            }
        }
        assertThat(exportMethod).isNotNull();

        com.bank.branch.platform.common.aop.annotation.AuditLog auditLog =
                exportMethod.getAnnotation(com.bank.branch.platform.common.aop.annotation.AuditLog.class);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.reasonRequired()).isTrue();
        assertThat(auditLog.resourceType()).isEqualTo("TAG");
    }
}
