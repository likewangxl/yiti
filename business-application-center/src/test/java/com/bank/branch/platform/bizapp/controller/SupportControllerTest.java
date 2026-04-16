package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.service.SupportService;
import com.bank.branch.platform.bizapp.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.bizapp.support.WithMockEmpContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SupportController 集成测试。
 * 继承 AbstractControllerIntegrationTest，通过 @MockBean 替换 Service 层。
 */
class SupportControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SupportService supportService;

    // ==================== GET /api/support-requests ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPage_shouldReturn200() throws Exception {
        SupportRequest sr = buildRequest("SR001");
        PageResult<SupportRequest> page = PageResult.of(1, 20, 1L, List.of(sr));

        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.listPage(isNull(), isNull(), eq("ORG001"), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/support-requests")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ==================== GET /api/support-requests/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getById_shouldReturn200() throws Exception {
        SupportRequest sr = buildRequest("SR001");
        when(supportService.getById("SR001")).thenReturn(sr);

        mockMvc.perform(get("/api/support-requests/SR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("SR001"));
    }

    // ==================== POST /api/support-requests ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void create_shouldReturn200() throws Exception {
        SupportRequest sr = buildRequest("SR001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.create(any(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of(sr));

        String body = "{\"custId\":\"CUST001\"}";

        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/support-requests/{id}/submit ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void submit_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        doNothing().when(supportService).submit(eq("SR001"), anyString(), anyString());

        mockMvc.perform(post("/api/support-requests/SR001/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== DELETE /api/support-requests/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void delete_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(supportService).deleteDraft(eq("SR001"), anyString());

        mockMvc.perform(delete("/api/support-requests/SR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/support-requests/{id}/cancel ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancel_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(supportService).cancel(eq("SR001"), anyString());

        mockMvc.perform(post("/api/support-requests/SR001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== GET /api/support-requests/export ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void export_shouldReturn501() throws Exception {
        mockMvc.perform(get("/api/support-requests/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("501"));
    }

    // ==================== GET /api/support-requests/available-products ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listAvailableProducts_shouldReturn200() throws Exception {
        ProductDTO product = ProductDTO.builder()
                .id("P001")
                .productCode("PC001")
                .productName("产品A")
                .supportForSupportRequest(true)
                .build();
        when(productApi.listSupportAvailableProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/support-requests/available-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== 辅助方法 ====================

    private SupportRequest buildRequest(String id) {
        SupportRequest sr = new SupportRequest();
        sr.setId(id);
        sr.setRequestNo("SR20260414000001");
        sr.setCustId("CUST001");
        sr.setStatus(SupportStatus.DRAFT.getCode());
        sr.setOwnerOrgId("ORG001");
        sr.setCreatedBy("E10001");
        sr.setDeleted(0);
        return sr;
    }
}
