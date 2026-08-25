package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.customer.dto.req.TagCustomerImportRow;
import com.bank.branch.platform.customer.dto.resp.TagCustomerImportResultDTO;
import com.bank.branch.platform.customer.service.TagCustomerService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TagCustomerImportControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TagCustomerService tagCustomerService;

    @Test
    @WithMockEmpContext(empId = "E10001")
    void importFile_shouldAcceptMultipartExcelAndMode() throws Exception {
        TagCustomerImportResultDTO result = new TagCustomerImportResultDTO();
        result.setSuccess(true);
        result.setImportedCount(2);
        result.setMode("APPEND");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(tagCustomerService.importCustomersFile(eq("tag-001"), any(), eq("APPEND"), eq("E10001")))
                .thenReturn(result);
        MockMultipartFile file = new MockMultipartFile("file", "customers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/tags/tag-001/customers/import-file")
                        .file(file).param("mode", "APPEND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.importedCount").value(2));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void importTemplate_shouldReturnXlsxAttachment() throws Exception {
        TagCustomerImportRow row = new TagCustomerImportRow();
        row.setTagName("重点项目");
        row.setTagDescription("重点项目客户");
        when(tagCustomerService.getImportTemplateRow("tag-001")).thenReturn(row);

        mockMvc.perform(get("/api/tags/tag-001/customers/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }
}
