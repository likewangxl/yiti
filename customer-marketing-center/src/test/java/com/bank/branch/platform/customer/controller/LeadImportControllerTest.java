package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.resp.LeadImportPreviewResp;
import com.bank.branch.platform.customer.entity.LeadImportBatch;
import com.bank.branch.platform.customer.enums.BatchStatus;
import com.bank.branch.platform.customer.service.LeadImportService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LeadImportController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest，通过 @MockBean 替换 Service 层真实业务逻辑。
 * </p>
 */
class LeadImportControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LeadImportService leadImportService;

    // ==================== POST /api/leads/import/preview ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void preview_shouldReturn200() throws Exception {
        LeadImportPreviewResp resp = new LeadImportPreviewResp();
        resp.setBatchId("batch-001");
        resp.setBatchNo("BATCH_20260414_0001");
        resp.setTotalRows(10);
        resp.setErrorRows(0);

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(leadImportService.preview(any(), anyString(), anyString())).thenReturn(resp);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv",
                "客户名称,统一信用代码\n企业A,123\n".getBytes()
        );

        mockMvc.perform(multipart("/api/leads/import/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.batchId").value("batch-001"))
                .andExpect(jsonPath("$.data.totalRows").value(10));
    }

    // ==================== POST /api/leads/import/execute ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void execute_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        doNothing().when(leadImportService).execute(anyString(), anyString(), anyString());

        String body = "{\"batchId\":\"batch-001\"}";

        mockMvc.perform(post("/api/leads/import/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== GET /api/leads/batches ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listBatches_shouldReturn200() throws Exception {
        LeadImportBatch batch = buildBatch("batch-001");
        PageResult<LeadImportBatch> page = PageResult.of(1, 20, 1L, List.of(batch));

        when(leadImportService.listBatches(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/leads/batches")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ============================= 辅助方法 =============================

    private LeadImportBatch buildBatch(String id) {
        LeadImportBatch batch = new LeadImportBatch();
        batch.setId(id);
        batch.setBatchNo("BATCH_20260414_0001");
        batch.setStatus(BatchStatus.CREATED.getCode());
        batch.setTotalRowCount(10);
        batch.setErrorRowCount(0);
        batch.setOwnerOrgId("ORG001");
        batch.setCreatedBy("E10001");
        return batch;
    }
}
