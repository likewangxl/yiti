package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.dto.resp.CustomerCrossOrgHistoryVO;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.service.CustomerService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CustomerHistoryController 集成测试。
 *
 * <p>
 * 覆盖高危端点 GET /api/customers/{id}/history 的核心场景：
 * - 正常聚合返回（happy path）
 * - 客户不存在时返回 CUST-40403
 * </p>
 *
 * <p>
 * 审计要求：该端点为高危/跨机构操作，@AuditLog(reasonRequired=true) 已在 Controller 层声明。
 * </p>
 */
class CustomerHistoryControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CustomerService customerService;

    // ==================== GET /api/customers/{id}/history ====================

    /**
     * 正常情况：返回聚合历史 VO，包含 customer / claims / touchTasks 字段。
     */
    @Test
    @WithMockEmpContext(empId = "E10001")
    void getHistory_returnsAggregatedVO() throws Exception {
        CustomerCrossOrgHistoryVO vo = new CustomerCrossOrgHistoryVO();
        CustomerDTO customer = new CustomerDTO();
        customer.setId("C001");
        customer.setCustName("测试客户");
        vo.setCustomer(customer);
        vo.setClaims(Collections.emptyList());
        vo.setTouchTasks(Collections.emptyList());
        vo.setTotalClaimCount(0L);
        vo.setActiveClaimCount(0L);
        vo.setTotalTouchCount(0L);

        when(customerService.getCrossOrgHistory("C001")).thenReturn(vo);

        mockMvc.perform(get("/api/customers/{id}/history", "C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.customer").exists())
                .andExpect(jsonPath("$.data.customer.custName").value("测试客户"))
                .andExpect(jsonPath("$.data.claims").isArray())
                .andExpect(jsonPath("$.data.touchTasks").isArray())
                .andExpect(jsonPath("$.data.totalClaimCount").value(0))
                .andExpect(jsonPath("$.data.activeClaimCount").value(0))
                .andExpect(jsonPath("$.data.totalTouchCount").value(0));
    }

    /**
     * 异常情况：客户不存在时，GlobalExceptionHandler 返回 HTTP 200，code 为 CUST-40403。
     */
    @Test
    @WithMockEmpContext(empId = "E10001")
    void getHistory_returns404WhenCustomerNotFound() throws Exception {
        when(customerService.getCrossOrgHistory("NA"))
                .thenThrow(new BizException(
                        CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                        CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage()));

        mockMvc.perform(get("/api/customers/{id}/history", "NA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode()));
    }
}
