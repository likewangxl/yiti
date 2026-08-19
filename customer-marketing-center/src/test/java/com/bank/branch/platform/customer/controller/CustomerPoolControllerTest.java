package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.service.CustomerPoolService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CustomerPoolController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest 基类，通过 @MockBean CustomerPoolService 替换真实业务逻辑。
 * </p>
 */
class CustomerPoolControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CustomerPoolService customerPoolService;

    // ==================== GET /api/customer-pool ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPool_returnsCustomerDTOFields() throws Exception {
        CustomerDTO c1 = new CustomerDTO();
        c1.setId("cust-001");
        c1.setCustName("测试客户A");
        c1.setCustNo("C001");
        CustomerDTO c2 = new CustomerDTO();
        c2.setId("cust-002");
        c2.setCustName("测试客户B");
        c2.setCustNo("C002");

        PageResult<CustomerDTO> page = PageResult.of(1, 20, 2L, Arrays.asList(c1, c2));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(customerPoolService.listPoolAsDTO(isNull(), eq("E10001"), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/customer-pool")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.page.records[0].custName").value("测试客户A"))
                .andExpect(jsonPath("$.page.records[0].deleted").doesNotExist())
                .andExpect(jsonPath("$.page.records[0].createdTime").doesNotExist());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPool_shouldPassKeywordToService() throws Exception {
        PageResult<CustomerDTO> emptyPage = PageResult.of(1, 20, 0L, Collections.emptyList());
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(customerPoolService.listPoolAsDTO(eq("关键词"), eq("E10001"), eq(1), eq(20))).thenReturn(emptyPage);

        // when/then
        mockMvc.perform(get("/api/customer-pool")
                        .param("keyword", "关键词")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(0));
    }
}
