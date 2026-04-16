package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.CustMasterStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.service.CustomerService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CustomerController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest 基类，通过 @MockBean CustomerService 替换真实业务逻辑。
 * </p>
 */
class CustomerControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CustomerService customerService;

    // ==================== GET /api/customers ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPage_shouldReturn200() throws Exception {
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        master.setCustName("测试客户");
        master.setStatus(CustMasterStatus.ACTIVE.getCode());
        PageResult<CustMaster> page = PageResult.of(1, 20, 1L, Collections.singletonList(master));

        when(customerService.listPage(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/customers")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].custName").value("测试客户"));
    }

    // ==================== GET /api/customers/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getById_shouldReturn200() throws Exception {
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        master.setCustName("测试客户");
        master.setCustNo("CUST_00001");
        when(customerService.getById("cust-001")).thenReturn(master);

        mockMvc.perform(get("/api/customers/cust-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("cust-001"))
                .andExpect(jsonPath("$.data.custName").value("测试客户"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getById_shouldReturn400WhenNotFound() throws Exception {
        when(customerService.getById("not-exist"))
                .thenThrow(new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                        CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage()));

        mockMvc.perform(get("/api/customers/not-exist"))
                .andExpect(status().isOk())  // GlobalExceptionHandler 统一返回 200，code 非 0
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode()));
    }

    // ==================== POST /api/customers/{custId}/claims/{claimId}/transfer ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void transfer_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(customerService).transfer(
                eq("claim-001"), eq("E10002"), eq("员工离职交接"), eq("E10001"));

        String body = "{\"toEmpId\":\"E10002\",\"reason\":\"员工离职交接\"}";

        mockMvc.perform(post("/api/customers/cust-001/claims/claim-001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void transfer_shouldReturn400WhenReasonBlank() throws Exception {
        // reason 为空，由 DTO @NotBlank 校验层拦截，GlobalExceptionHandler 返回 HTTP 400
        String body = "{\"toEmpId\":\"E10002\",\"reason\":\"\"}";

        mockMvc.perform(post("/api/customers/cust-001/claims/claim-001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    // ==================== POST /api/customers/{custId}/delete-apply ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void deleteApply_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        doNothing().when(customerService).deleteApply(eq("cust-001"), eq("E10001"), eq("ORG_SZ_001"));

        String body = "{\"reason\":\"客户已失效\"}";

        mockMvc.perform(post("/api/customers/cust-001/delete-apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void deleteApply_shouldReturn400WhenCustomerNotFound() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        doThrow(new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage()))
                .when(customerService).deleteApply(eq("not-exist"), anyString(), anyString());

        String body = "{\"reason\":\"测试\"}";

        mockMvc.perform(post("/api/customers/not-exist/delete-apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode()));
    }
}
