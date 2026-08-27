package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerTagImportControllerTest {

    @Mock
    private MarketingCustomerTagImportService importService;
    @Mock
    private CurrentUserApi currentUserApi;
    @InjectMocks
    private MarketingCustomerTagImportController controller;

    @Test
    void importTemplateReturnsXlsxAttachmentWithFixedFileName() throws Exception {
        byte[] expected = "xlsx".getBytes(StandardCharsets.UTF_8);
        when(importService.importTemplate()).thenReturn(expected);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.importTemplate(response);

        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename*=UTF-8''%E8%90%A5%E9%94%80%E5%AE%A2%E6%88%B7%E6%A0%87%E7%AD%BE%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xlsx");
        assertThat(response.getContentAsByteArray()).containsExactly(expected);
        verify(importService).importTemplate();
    }
}
