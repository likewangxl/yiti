package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.controller.MarketingLeadController;
import com.bank.branch.platform.customer.dto.marketing.lead.MarketingCustomerSnapshot;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 营销线索 REST 查询参数契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingLeadControllerTest {

    @Mock
    MarketingLeadEntryService service;
    @Mock
    CurrentUserApi currentUserApi;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MarketingLeadController(service, currentUserApi)).build();
    }

    @Test
    void lookupAcceptsCustomerNameWithoutCreditCode() throws Exception {
        MarketingCustomerSnapshot snapshot = new MarketingCustomerSnapshot();
        snapshot.setCustName("测试企业");
        when(service.lookupCustomer("测试企业", null)).thenReturn(snapshot);

        mockMvc.perform(get("/api/marketing/leads/lookup")
                        .param("customerName", "测试企业"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.custName").value("测试企业"));

        verify(service).lookupCustomer("测试企业", null);
    }
}
