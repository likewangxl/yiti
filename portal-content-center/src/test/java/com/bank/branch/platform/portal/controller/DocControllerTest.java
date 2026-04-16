package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.service.DocService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DocController 集成测试 -- 公开只读接口
 *
 * <p>使用 @MockBean 替换 DocService，避免拖入数据库依赖。
 * 验证 GET /api/documents 和 GET /api/documents/{id}/download 接口。</p>
 */
class DocControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    // ========== E.1 listDocuments ==========

    @Test
    @WithMockEmpContext(empId = "E10001", roleCodes = {"R_RM"})
    void listDocuments_returns200() throws Exception {
        DocInfo doc = buildDoc("doc-001", "测试文档", "POLICY", "ACTIVE");
        PageResult<DocInfo> page = PageResult.of(1, 20, 1L, List.of(doc));
        when(docService.listDocuments(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/documents").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].id").value("doc-001"))
                .andExpect(jsonPath("$.page.records[0].docTitle").value("测试文档"));
    }

    @Test
    @WithMockEmpContext
    void listDocuments_emptyResult() throws Exception {
        PageResult<DocInfo> emptyPage = PageResult.of(1, 20, 0L, Collections.emptyList());
        when(docService.listDocuments(any(), any(), any(), anyInt(), anyInt())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(0))
                .andExpect(jsonPath("$.page.records").isArray())
                .andExpect(jsonPath("$.page.records").isEmpty());
    }

    // ========== E.2 downloadDocument ==========

    @Test
    @WithMockEmpContext(empId = "E10001")
    void downloadDocument_returns200() throws Exception {
        when(docService.getDownloadUrl("doc-001"))
                .thenReturn("https://minio.local/bucket/file-001?token=abc");

        mockMvc.perform(get("/api/documents/doc-001/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("https://minio.local/bucket/file-001?token=abc"));
    }

    // ========== 辅助方法 ==========

    private DocInfo buildDoc(String id, String title, String category, String status) {
        DocInfo doc = new DocInfo();
        doc.setId(id);
        doc.setDocTitle(title);
        doc.setDocCategory(category);
        doc.setFileObjectId("file-" + id);
        doc.setStatus(status);
        doc.setCreatedBy("SYSTEM");
        doc.setCreatedTime(LocalDateTime.now());
        doc.setUpdatedBy("SYSTEM");
        doc.setUpdatedTime(LocalDateTime.now());
        return doc;
    }
}
