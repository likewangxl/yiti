package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.dto.resp.SubmitRespDTO;
import com.bank.branch.platform.bizapp.dto.resp.SupportRequestCreateRespDTO;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.service.SupportService;
import com.bank.branch.platform.bizapp.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.bizapp.support.WithMockEmpContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("GET /api/support-requests 返回 SupportRequestListItemDTO 列表，不含 deleted 字段")
    void listPage_returnsListItemDTO_notEntity() throws Exception {
        SupportRequestListItemDTO item = new SupportRequestListItemDTO();
        item.setId("SR001");
        item.setRequestNo("SR20260414000001");
        item.setCustName("测试客户");
        item.setStatus(SupportStatus.DRAFT.getCode());
        PageResult<SupportRequestListItemDTO> page = PageResult.of(1, 20, 1L, List.of(item));

        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.listPageAsDTO(isNull(), isNull(), eq("ORG001"), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/support-requests")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].custName").value("测试客户"))
                .andExpect(jsonPath("$.page.records[0].deleted").doesNotExist());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPage_shouldReturn200() throws Exception {
        SupportRequestListItemDTO item = new SupportRequestListItemDTO();
        item.setId("SR001");
        item.setStatus(SupportStatus.DRAFT.getCode());
        PageResult<SupportRequestListItemDTO> page = PageResult.of(1, 20, 1L, List.of(item));

        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.listPageAsDTO(isNull(), isNull(), eq("ORG001"), eq(1), eq(20)))
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
    @DisplayName("GET /api/support-requests/{id} 返回 SupportRequestDTO，不含 deleted 字段")
    void getById_returnsDTO_notEntity() throws Exception {
        SupportRequestDTO dto = new SupportRequestDTO();
        dto.setId("SR001");
        dto.setRequestNo("SR20260414000001");
        dto.setCustName("测试客户");
        dto.setStatus(SupportStatus.DRAFT.getCode());
        when(supportService.getByIdAsDTO("SR001")).thenReturn(dto);

        mockMvc.perform(get("/api/support-requests/SR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("SR001"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getById_shouldReturn200() throws Exception {
        SupportRequestDTO dto = new SupportRequestDTO();
        dto.setId("SR001");
        dto.setStatus(SupportStatus.DRAFT.getCode());
        when(supportService.getByIdAsDTO("SR001")).thenReturn(dto);

        mockMvc.perform(get("/api/support-requests/SR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("SR001"));
    }

    // ==================== POST /api/support-requests ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    @DisplayName("POST /api/support-requests 返回 SupportRequestCreateRespDTO 含 submitGroupId")
    void create_returnsCreateRespWithSubmitGroupId() throws Exception {
        SupportRequestCreateRespDTO resp = SupportRequestCreateRespDTO.builder()
                .submitGroupId("grp001")
                .productCount(2)
                .requests(List.of(
                        SupportRequestCreateRespDTO.CreatedItem.builder().id("sr001").scenario("A").build(),
                        SupportRequestCreateRespDTO.CreatedItem.builder().id("sr002").scenario("A").build()))
                .build();
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.create(any(), anyString(), any(), any(), any(), anyString(), anyString(),
                any(), any(), any()))
                .thenReturn(resp);

        String body = "{\"custId\":\"CUST001\"}";

        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submitGroupId").value("grp001"))
                .andExpect(jsonPath("$.data.productCount").value(2))
                .andExpect(jsonPath("$.data.requests", hasSize(2)));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void create_shouldReturn200() throws Exception {
        SupportRequestCreateRespDTO resp = SupportRequestCreateRespDTO.builder()
                .submitGroupId("grp001")
                .productCount(1)
                .requests(List.of(
                        SupportRequestCreateRespDTO.CreatedItem.builder().id("sr001").scenario("B").build()))
                .build();
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.create(any(), anyString(), any(), any(), any(), anyString(), anyString(),
                any(), any(), any()))
                .thenReturn(resp);

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
    @DisplayName("POST /api/support-requests/{id}/submit 返回 SubmitRespDTO 含 processInstanceId")
    void submit_returnsProcessInstanceId() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.submit(eq("SR001"), anyString(), anyString()))
                .thenReturn(new SubmitRespDTO("pi-xyz", "SUPPORT:SR001", "IN_APPROVAL"));

        mockMvc.perform(post("/api/support-requests/SR001/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processInstanceId").value("pi-xyz"))
                .andExpect(jsonPath("$.data.businessKey").value("SUPPORT:SR001"))
                .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void submit_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(supportService.submit(eq("SR001"), anyString(), anyString()))
                .thenReturn(new SubmitRespDTO("pi-sr001", "SUPPORT:SR001", "IN_APPROVAL"));

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

}
