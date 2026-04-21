package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.LeadApi;
import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.service.LeadService;
import com.bank.branch.platform.customer.service.LeadVersionService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LeadController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest，通过 @MockBean 替换 Service 层真实业务逻辑。
 * </p>
 */
class LeadControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LeadService leadService;

    @MockBean
    LeadVersionService leadVersionService;

    @MockBean
    LeadApi leadApi;

    // ==================== GET /api/leads ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPage_shouldReturn200() throws Exception {
        CustLead lead = buildLead("lead-001");
        PageResult<CustLead> page = PageResult.of(1, 20, 1L, List.of(lead));

        when(leadService.listPage(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/leads")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ==================== GET /api/leads/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getById_shouldReturn200() throws Exception {
        CustLead lead = buildLead("lead-001");
        when(leadService.getById("lead-001")).thenReturn(lead);

        mockMvc.perform(get("/api/leads/lead-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("lead-001"))
                .andExpect(jsonPath("$.data.custName").value("测试企业"));
    }

    // ==================== POST /api/leads ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void create_shouldReturn200() throws Exception {
        CustLead lead = buildLead("new-lead-001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(leadService.createDraft(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyString(), anyString()
        )).thenReturn(lead);

        String body = "{\"custName\":\"测试企业\",\"unifiedCreditCode\":\"91110000123456789X\"}";

        mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("new-lead-001"));
    }

    // ==================== PUT /api/leads/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void update_shouldReturn200() throws Exception {
        CustLead lead = buildLead("lead-001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(leadService.updateDraft(
                eq("lead-001"), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), anyString()
        )).thenReturn(lead);

        String body = "{\"custName\":\"更新企业名\"}";

        mockMvc.perform(put("/api/leads/lead-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== DELETE /api/leads/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void delete_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(leadService).deleteDraft(eq("lead-001"), anyString());

        mockMvc.perform(delete("/api/leads/lead-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/leads/{id}/submit ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void submit_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        doNothing().when(leadService).submitForApproval(eq("lead-001"), anyString(), anyString());

        mockMvc.perform(post("/api/leads/lead-001/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/leads/edit-version ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void createEditVersion_shouldReturn200() throws Exception {
        CustLead lead = buildLead("new-version-001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(leadVersionService.createEditVersion(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyString()
        )).thenReturn(lead);

        String body = "{\"sourceCustId\":\"cust-001\",\"custName\":\"更新企业名\"}";

        mockMvc.perform(post("/api/leads/edit-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("new-version-001"));
    }

    // ==================== POST /api/leads/delete-version ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void createDeleteVersion_shouldReturn200() throws Exception {
        CustLead lead = buildLead("delete-version-001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(leadVersionService.createDeleteVersion(anyString(), anyString(), anyString())).thenReturn(lead);

        String body = "{\"sourceCustId\":\"cust-001\"}";

        mockMvc.perform(post("/api/leads/delete-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("delete-version-001"));
    }

    // ==================== GET /api/leads/{id}/versions ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void versions_returnsVersionChain() throws Exception {
        // given: 版本链包含 v1、v2 两条记录
        LeadDTO v1 = new LeadDTO();
        v1.setId("L1_v1");
        v1.setVersionNo(1);
        v1.setCustName("测试企业");

        LeadDTO v2 = new LeadDTO();
        v2.setId("L1_v2");
        v2.setVersionNo(2);
        v2.setCustName("测试企业-更新");

        when(leadApi.getLeadVersionChain("L1")).thenReturn(List.of(v1, v2));

        mockMvc.perform(get("/api/leads/{id}/versions", "L1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("L1_v1"))
                .andExpect(jsonPath("$.data[1].id").value("L1_v2"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void versions_returnsEmptyForNonExistent() throws Exception {
        // given: 不存在的线索，返回空列表
        when(leadApi.getLeadVersionChain("NA")).thenReturn(List.of());

        mockMvc.perform(get("/api/leads/{id}/versions", "NA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ============================= 辅助方法 =============================

    private CustLead buildLead(String id) {
        CustLead lead = new CustLead();
        lead.setId(id);
        lead.setLeadNo("LEAD_20260414_0001");
        lead.setLeadStatus(LeadStatus.DRAFT.getCode());
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("测试企业");
        lead.setCreatedBy("E10001");
        lead.setOwnerOrgId("ORG001");
        lead.setDeleted(0);
        return lead;
    }
}
