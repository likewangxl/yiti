package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.service.DocService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminDocController 集成测试 -- 管理端文档 CRUD 接口
 *
 * <p>使用 @MockBean 替换 DocService，避免拖入数据库依赖。
 * 验证 POST/PUT/DELETE /api/admin/documents 接口的请求响应格式。</p>
 */
class AdminDocControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockBean DocService docService;
    @MockBean ProductService productService;
    @MockBean ProductExportService productExportService;

    // ========== E.3 createDocument ==========

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void createDocument_returns200() throws Exception {
        DocInfo resultEntity = new DocInfo();
        resultEntity.setId("NEW_DOC_001");
        resultEntity.setDocTitle("新文档");
        when(docService.createDocument(any())).thenReturn(resultEntity);

        String body = "{\"docTitle\":\"新文档\",\"docCategory\":\"POLICY\",\"fileObjectId\":\"file-obj-001\"}";
        mockMvc.perform(post("/api/admin/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("NEW_DOC_001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void createDocument_returns400WhenDocTitleMissing() throws Exception {
        String body = "{\"docCategory\":\"POLICY\",\"fileObjectId\":\"file-obj-001\"}";
        mockMvc.perform(post("/api/admin/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ========== E.4 updateDocument ==========

    @Test
    @WithMockEmpContext(empId = "E10001")
    void updateDocument_returns200() throws Exception {
        doNothing().when(docService).updateDocument(eq("doc-001"), any());

        String body = "{\"docTitle\":\"更新后标题\"}";
        mockMvc.perform(put("/api/admin/documents/doc-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void updateDocument_returnsErrorWhenNotFound() throws Exception {
        doThrow(new BizException("PORTAL-40401", "文档不存在"))
                .when(docService).updateDocument(eq("doc-nonexist"), any());

        String body = "{\"docTitle\":\"不存在的文档\"}";
        mockMvc.perform(put("/api/admin/documents/doc-nonexist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40401"));
    }

    // ========== E.5 deleteDocument ==========

    @Test
    @WithMockEmpContext(empId = "E10001")
    void deleteDocument_returns200() throws Exception {
        doNothing().when(docService).deleteDocument(eq("doc-001"));

        mockMvc.perform(delete("/api/admin/documents/doc-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void deleteDocument_returnsErrorWhenNotFound() throws Exception {
        doThrow(new BizException("PORTAL-40401", "文档不存在"))
                .when(docService).deleteDocument(eq("doc-nonexist"));

        mockMvc.perform(delete("/api/admin/documents/doc-nonexist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40401"));
    }
}
