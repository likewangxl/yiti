package com.bank.branch.platform.customer.marketing.tag;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.controller.MarketingCustomerTagController;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 营销客户标签列表 REST 查询参数契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingCustomerTagControllerTest {

    @Mock
    MarketingCustomerTagService tagService;
    @Mock
    CurrentUserApi currentUserApi;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MarketingCustomerTagController(tagService, currentUserApi)).build();
    }

    @Test
    void listPassesTagTypeAndViewStatusWithoutDroppingLegacyFilters() throws Exception {
        when(tagService.list("客户", "价值类", "RULE", "DISABLED", "APPROVED",
                "EXCEPTION", 2, 30))
                .thenReturn(PageResult.of(2, 30, 0, List.<MarketingCustomerTag>of()));

        mockMvc.perform(get("/api/marketing/customer-tags")
                        .param("keyword", "客户")
                        .param("category", "价值类")
                        .param("tagType", "RULE")
                        .param("status", "DISABLED")
                        .param("approvalStatus", "APPROVED")
                        .param("viewStatus", "EXCEPTION")
                        .param("pageNo", "2")
                        .param("pageSize", "30"))
                .andExpect(status().isOk());

        verify(tagService).list("客户", "价值类", "RULE", "DISABLED", "APPROVED",
                "EXCEPTION", 2, 30);
    }
}
