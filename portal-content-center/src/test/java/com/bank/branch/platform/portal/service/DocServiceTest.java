package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.portal.controller.dto.doc.DocumentCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.doc.DocumentUpdateReqDTO;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.DocInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocService 单元测试 -- 纯 JUnit 5 + Mockito，无需 Spring 上下文
 *
 * <p>TDD RED-GREEN 闭环：先写测试（Red），再实现 Service（Green）。
 * 涵盖分页查询、详情查询、创建、更新、删除、下载链接等核心场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class DocServiceTest {

    @Mock DocInfoMapper docInfoMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock FileApi fileApi;
    @Mock AuditApi auditApi;
    @InjectMocks DocService docService;

    // ========== listDocuments ==========

    @Test
    void listDocuments_withPagination() {
        // 模拟 2 条记录、总数 2
        DocInfo doc1 = buildDoc("doc-001", "测试文档1", "POLICY", "ACTIVE");
        DocInfo doc2 = buildDoc("doc-002", "测试文档2", "MANUAL", "ACTIVE");
        when(docInfoMapper.countPage(null, null, null)).thenReturn(2L);
        when(docInfoMapper.selectPage(null, null, null, 0, 20))
                .thenReturn(Arrays.asList(doc1, doc2));

        PageResult<DocInfo> result = docService.listDocuments(null, null, null, 1, 20);

        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(2);
        verify(docInfoMapper).countPage(null, null, null);
        verify(docInfoMapper).selectPage(null, null, null, 0, 20);
    }

    @Test
    void listDocuments_emptyResult() {
        when(docInfoMapper.countPage("不存在", null, null)).thenReturn(0L);

        PageResult<DocInfo> result = docService.listDocuments("不存在", null, null, 1, 20);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        // total=0 时不应该查询 selectPage
        verify(docInfoMapper, never()).selectPage(anyString(), any(), any(), anyInt(), anyInt());
    }

    // ========== getDocument ==========

    @Test
    void getDocument_success() {
        DocInfo doc = buildDoc("doc-001", "测试文档", "POLICY", "ACTIVE");
        when(docInfoMapper.selectById("doc-001")).thenReturn(doc);

        DocInfo result = docService.getDocument("doc-001");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("doc-001");
        assertThat(result.getDocTitle()).isEqualTo("测试文档");
    }

    @Test
    void getDocument_notFound() {
        when(docInfoMapper.selectById("doc-nonexist")).thenReturn(null);

        assertThatThrownBy(() -> docService.getDocument("doc-nonexist"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.DOC_NOT_FOUND.getCode());
                });
    }

    // ========== getDownloadUrl ==========

    @Test
    void getDownloadUrl_success() {
        DocInfo doc = buildDoc("doc-001", "测试文档", "POLICY", "ACTIVE");
        doc.setFileObjectId("file-obj-001");
        when(docInfoMapper.selectById("doc-001")).thenReturn(doc);
        when(fileApi.getDownloadUrl("file-obj-001")).thenReturn("https://minio.local/bucket/file-obj-001?token=abc");

        String url = docService.getDownloadUrl("doc-001");

        assertThat(url).isEqualTo("https://minio.local/bucket/file-obj-001?token=abc");
        verify(fileApi).getDownloadUrl("file-obj-001");
    }

    // ========== createDocument ==========

    @Test
    void createDocument_success() {
        DocumentCreateReqDTO req = new DocumentCreateReqDTO();
        req.setDocTitle("新文档");
        req.setDocCategory("POLICY");
        req.setFileObjectId("file-obj-001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        DocInfo result = docService.createDocument(req);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getDocTitle()).isEqualTo("新文档");
        assertThat(result.getDocCategory()).isEqualTo("POLICY");
        assertThat(result.getFileObjectId()).isEqualTo("file-obj-001");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCreatedBy()).isEqualTo("OPERATOR01");

        ArgumentCaptor<DocInfo> captor = ArgumentCaptor.forClass(DocInfo.class);
        verify(docInfoMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isNotBlank();
        verify(auditApi).log(any());
    }

    // ========== updateDocument ==========

    @Test
    void updateDocument_success() {
        DocInfo existing = buildDoc("doc-001", "旧标题", "POLICY", "ACTIVE");
        when(docInfoMapper.selectById("doc-001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        DocumentUpdateReqDTO req = new DocumentUpdateReqDTO();
        req.setDocTitle("新标题");

        docService.updateDocument("doc-001", req);

        ArgumentCaptor<DocInfo> captor = ArgumentCaptor.forClass(DocInfo.class);
        verify(docInfoMapper).updateById(captor.capture());
        DocInfo patch = captor.getValue();
        assertThat(patch.getId()).isEqualTo("doc-001");
        assertThat(patch.getDocTitle()).isEqualTo("新标题");
        assertThat(patch.getUpdatedBy()).isEqualTo("OPERATOR01");
        verify(auditApi).log(any());
    }

    @Test
    void updateDocument_notFound() {
        when(docInfoMapper.selectById("doc-nonexist")).thenReturn(null);

        DocumentUpdateReqDTO req = new DocumentUpdateReqDTO();
        req.setDocTitle("新标题");

        assertThatThrownBy(() -> docService.updateDocument("doc-nonexist", req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.DOC_NOT_FOUND.getCode());
                });

        verify(docInfoMapper, never()).updateById(any());
    }

    // ========== deleteDocument ==========

    @Test
    void deleteDocument_success() {
        DocInfo existing = buildDoc("doc-001", "测试文档", "POLICY", "ACTIVE");
        when(docInfoMapper.selectById("doc-001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        docService.deleteDocument("doc-001");

        verify(docInfoMapper).softDeleteById("doc-001", "OPERATOR01");
        verify(auditApi).log(any());
    }

    @Test
    void deleteDocument_notFound() {
        when(docInfoMapper.selectById("doc-nonexist")).thenReturn(null);

        assertThatThrownBy(() -> docService.deleteDocument("doc-nonexist"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.DOC_NOT_FOUND.getCode());
                });

        verify(docInfoMapper, never()).softDeleteById(anyString(), anyString());
    }

    // ========== listActiveByCategory ==========

    @Test
    void listActiveByCategory_returnsMatchingDocs() {
        DocInfo doc1 = buildDoc("doc-001", "政策文档1", "POLICY", "ACTIVE");
        DocInfo doc2 = buildDoc("doc-002", "政策文档2", "POLICY", "ACTIVE");
        when(docInfoMapper.listActiveByCategory("POLICY")).thenReturn(Arrays.asList(doc1, doc2));

        List<DocInfo> result = docService.listActiveByCategory("POLICY");

        assertThat(result).hasSize(2);
        verify(docInfoMapper).listActiveByCategory("POLICY");
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
