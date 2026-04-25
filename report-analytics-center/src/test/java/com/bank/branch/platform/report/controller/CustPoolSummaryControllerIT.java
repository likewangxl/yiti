package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户池汇总 C.4 端到端 IT（Task M3.3.1 + M3.3.2，Red→Green）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>GET /api/reports/customer-pool-summary：返回 4 字段 VIP/NORMAL/POTENTIAL 累计</li>
 *   <li>POST /api/reports/customer-pool-summary/export：返回 PENDING 状态任务</li>
 * </ol>
 */
class CustPoolSummaryControllerIT extends BaseControllerIT {

    @Test
    void getCustPoolSummary_returns200_withBreakdown() throws Exception {
        // total
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && (filter.getCustomerTypes() == null || filter.getCustomerTypes().isEmpty()))))
                .thenReturn(50L);
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && filter.getCustomerTypes() != null && filter.getCustomerTypes().contains("VIP"))))
                .thenReturn(10L);
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && filter.getCustomerTypes() != null && filter.getCustomerTypes().contains("NORMAL"))))
                .thenReturn(30L);
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && filter.getCustomerTypes() != null && filter.getCustomerTypes().contains("POTENTIAL"))))
                .thenReturn(10L);

        mvc.perform(get("/api/reports/customer-pool-summary")
                        .param("orgId", "BR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].orgCode").value("BR001"))
                .andExpect(jsonPath("$.page.records[0].totalCount").value(50))
                .andExpect(jsonPath("$.page.records[0].vipCount").value(10))
                .andExpect(jsonPath("$.page.records[0].normalCount").value(30))
                .andExpect(jsonPath("$.page.records[0].potentialCount").value(10));
    }

    @Test
    void submitCustPoolSummaryExport_returnsPending_withTaskId() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES");
        // 防御性：上游可能被无关 stub 触发，这里给个默认值
        when(customerQueryApi.countCustomers(any(CustomerFilterDTO.class))).thenReturn(0L);

        String body = """
                {
                  "orgId": "BR001"
                }
                """;

        mvc.perform(post("/api/reports/customer-pool-summary/export")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty());
    }
}
