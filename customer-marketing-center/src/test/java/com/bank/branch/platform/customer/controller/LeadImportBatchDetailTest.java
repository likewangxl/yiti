package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.customer.entity.LeadImportBatch;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.service.LeadImportService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LeadImportController.getBatch 集成测试（GET /api/leads/import/batches/{batchId}）。
 */
class LeadImportBatchDetailTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LeadImportService leadImportService;

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void getBatch_exists_returns200WithFields() throws Exception {
        LeadImportBatch entity = new LeadImportBatch();
        entity.setId("batch-001");
        entity.setBatchNo("IMP_20260428_001");
        entity.setSourceFileName("leads_2026Q2.xlsx");
        entity.setStatus("COMPLETED");
        entity.setTotalRowCount(150);
        entity.setErrorRowCount(2);
        entity.setBusinessKey("LEAD:IMP_batch-001");
        entity.setOwnerOrgId("ORG_SZ_001");
        entity.setCreatedBy("E10001");
        entity.setCreatedTime(LocalDateTime.now());

        when(leadImportService.getBatchById(eq("batch-001"))).thenReturn(entity);

        mockMvc.perform(get("/api/leads/import/batches/{id}", "batch-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("batch-001"))
                .andExpect(jsonPath("$.data.batchNo").value("IMP_20260428_001"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRowCount").value(150))
                .andExpect(jsonPath("$.data.errorRowCount").value(2))
                .andExpect(jsonPath("$.data.businessKey").value("LEAD:IMP_batch-001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void getBatch_notExists_returnsCust40406() throws Exception {
        when(leadImportService.getBatchById(eq("not-exist"))).thenReturn(null);

        mockMvc.perform(get("/api/leads/import/batches/{id}", "not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.BATCH_NOT_FOUND.getCode()));
    }
}
