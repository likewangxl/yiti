package com.bank.branch.platform.customer.marketing.customer;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.controller.MarketingCustomerController;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerQuery;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerTransferRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerVO;
import com.bank.branch.platform.customer.service.marketing.CustomerOwnershipService;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerControllerTest {

    @Mock
    MarketingCustomerService customerService;
    @Mock
    CustomerOwnershipService ownershipService;
    @Mock
    CurrentUserApi currentUserApi;
    @Mock
    BizScopeApi bizScopeApi;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketingCustomerController(
                customerService, ownershipService, currentUserApi, bizScopeApi)).build();
    }

    @Test
    void listMine_shouldUseCurrentUserAndReturnPage() throws Exception {
        MarketingCustomerVO customer = new MarketingCustomerVO();
        customer.setId(1L);
        customer.setCustName("测试客户");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(customerService.listMine(any(MarketingCustomerQuery.class), eq("E10001"), eq("ORG001")))
                .thenReturn(PageResult.of(1, 20, 1, List.of(customer)));

        mockMvc.perform(get("/api/marketing/customers/mine").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].custName").value("测试客户"));

        verify(customerService).listMine(any(MarketingCustomerQuery.class), eq("E10001"), eq("ORG001"));
    }

    @Test
    void transfer_shouldForwardCurrentUserAndRequest() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        mockMvc.perform(post("/api/marketing/customers/10/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferAction\":\"TRANSFER\",\"targetManagerId\":\"E10002\","
                                + "\"reason\":\"岗位调整\",\"lockVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(ownershipService).transfer(eq(10L), any(MarketingCustomerTransferRequest.class), eq("E10001"),
                eq("ORG001"), eq(false));
    }
}
