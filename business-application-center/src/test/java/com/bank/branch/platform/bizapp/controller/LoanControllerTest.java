package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyListItemDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.bizapp.service.LoanFormValidator;
import com.bank.branch.platform.bizapp.service.LoanService;
import com.bank.branch.platform.bizapp.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.bizapp.support.WithMockEmpContext;
import com.bank.branch.platform.common.web.PageResult;
import org.junit.jupiter.api.DisplayName;
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
 * LoanController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest，通过 @MockBean 替换 Service 层真实业务逻辑。
 * </p>
 */
class LoanControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LoanService loanService;

    @MockBean
    LoanFormValidator loanFormValidator;

    // ==================== GET /api/loans ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    @DisplayName("GET /api/loans 返回 LoanApplyListItemDTO 列表，不含 deleted 字段")
    void listPage_returnsListItemDTO_notEntity() throws Exception {
        LoanApplyListItemDTO item = new LoanApplyListItemDTO();
        item.setId("L001");
        item.setApplyNo("LA20260414000001");
        item.setCustName("测试客户");
        item.setStatus(LoanStatus.DRAFT.getCode());
        PageResult<LoanApplyListItemDTO> page = PageResult.of(1, 20, 1L, List.of(item));

        when(loanService.listPageAsDTO(isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/loans")
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
        LoanApplyListItemDTO item = new LoanApplyListItemDTO();
        item.setId("L001");
        item.setStatus(LoanStatus.DRAFT.getCode());
        PageResult<LoanApplyListItemDTO> page = PageResult.of(1, 20, 1L, List.of(item));

        when(loanService.listPageAsDTO(isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/loans")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ==================== GET /api/loans/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getById_shouldReturn200() throws Exception {
        LoanApply loan = buildLoan("L001");
        when(loanService.getById("L001")).thenReturn(loan);

        mockMvc.perform(get("/api/loans/L001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("L001"));
    }

    // ==================== POST /api/loans ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void create_shouldReturn200() throws Exception {
        LoanApply loan = buildLoan("L001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(loanService.createDraft(
                anyString(), any(), any(), any(), any(), any(), any(), anyString(), anyString()
        )).thenReturn(loan);

        String body = "{\"custId\":\"CUST001\"}";

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("L001"));
    }

    // ==================== PUT /api/loans/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void update_shouldReturn200() throws Exception {
        LoanApply loan = buildLoan("L001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(loanService.updateDraft(
                eq("L001"), any(), any(), any(), any(), any(), anyString()
        )).thenReturn(loan);

        String body = "{\"guaranteeType\":\"MORTGAGE\"}";

        mockMvc.perform(put("/api/loans/L001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/loans/{id}/submit ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void submit_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        doNothing().when(loanService).submitForApproval(eq("L001"), anyString(), anyString());

        mockMvc.perform(post("/api/loans/L001/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== DELETE /api/loans/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void delete_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(loanService).deleteDraft(eq("L001"), anyString());

        mockMvc.perform(delete("/api/loans/L001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/loans/{id}/cancel ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancel_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(loanService).cancelApply(eq("L001"), anyString());

        mockMvc.perform(post("/api/loans/L001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== GET /api/loans/export ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void export_shouldReturn501() throws Exception {
        mockMvc.perform(get("/api/loans/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("501"));
    }

    // ==================== GET /api/loans/{id}/node-form/{nodeKey} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getNodeForm_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/loans/L001/node-form/loan_corp_review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== 辅助方法 ====================

    private LoanApply buildLoan(String id) {
        LoanApply loan = new LoanApply();
        loan.setId(id);
        loan.setApplyNo("LA20260414000001");
        loan.setCustId("CUST001");
        loan.setStatus(LoanStatus.DRAFT.getCode());
        loan.setOwnerOrgId("ORG001");
        loan.setCreatedBy("E10001");
        loan.setDeleted(0);
        return loan;
    }
}
