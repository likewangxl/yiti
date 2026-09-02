package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadApprovalTaskResponse;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketingLeadApprovalControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MarketingLeadApprovalService service;
    @Mock
    private com.bank.branch.platform.auth.api.CurrentUserApi currentUserApi;
    @InjectMocks
    private MarketingLeadApprovalController controller;

    @Test
    void overviewPassesKeywordAndPaginationToService() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(service.overview("企业", 2, 10, "E001"))
                .thenReturn(PageResult.of(2, 10, 1, List.of(new LeadApprovalTaskResponse())));

        mockMvc.perform(get("/api/marketing/lead-approvals/overview")
                        .param("keyword", "企业")
                        .param("pageNo", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.pageNo").value(2))
                .andExpect(jsonPath("$.page.pageSize").value(10))
                .andExpect(jsonPath("$.page.total").value(1));

        verify(service).overview("企业", 2, 10, "E001");
    }
}
