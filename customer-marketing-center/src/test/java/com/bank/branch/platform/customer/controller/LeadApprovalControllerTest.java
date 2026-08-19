package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.resp.LeadRespDTO;
import com.bank.branch.platform.customer.service.LeadApprovalService;
import com.bank.branch.platform.customer.service.LeadEntryService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeadApprovalControllerTest extends AbstractControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private LeadApprovalService approvalService;
    @MockBean private LeadEntryService entryService;

    @Test
    @WithMockEmpContext(empId = "E001")
    void list_returnsWorkflowApprovalTasks() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        TaskRespDTO task = new TaskRespDTO();
        task.setBizId("LEAD-1");
        when(approvalService.list("PENDING", null, 1, 20, "E001"))
                .thenReturn(PageResult.of(1, 20, 1, List.of(task)));

        mockMvc.perform(get("/api/lead-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.records[0].bizId").value("LEAD-1"));
    }

    @Test
    @WithMockEmpContext(empId = "E001")
    void detail_returnsSameCompleteFormAsLeadEntry() throws Exception {
        LeadRespDTO detail = new LeadRespDTO();
        detail.setId("LEAD-1");
        detail.setCreditAmount(new java.math.BigDecimal("100"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(entryService.getVisibleDetail("LEAD-1", "E001", false)).thenReturn(detail);

        mockMvc.perform(get("/api/lead-approvals/LEAD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditAmount").value(100));
    }

    @Test
    @WithMockEmpContext(empId = "E001")
    void export_returnsXlsx() throws Exception {
        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ORG, "E001", "BR001", Set.of(), BizType.LEAD, BizAction.EXPORT);
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(bizScopeApi.buildScopeContext("E001", BizType.LEAD, BizAction.EXPORT)).thenReturn(scope);
        when(approvalService.exportReviewed(null, null, scope))
                .thenReturn(new byte[]{'P', 'K', 3, 4});

        mockMvc.perform(get("/api/lead-approvals/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(new byte[]{'P', 'K', 3, 4}));
    }
}
