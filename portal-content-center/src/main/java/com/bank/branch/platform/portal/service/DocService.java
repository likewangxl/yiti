package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.portal.controller.dto.doc.DocumentCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.doc.DocumentUpdateReqDTO;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.DocInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 文档管理业务逻辑服务
 *
 * <p>提供文档的分页查询、详情查询、新增、更新、逻辑删除和下载链接获取功能。
 * 文档删除采用逻辑删除（status=DISABLED），不删除 MinIO 上的文件本体。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocInfoMapper docInfoMapper;
    private final CurrentUserApi currentUserApi;
    private final FileApi fileApi;

    /**
     * 分页查询文档列表。
     *
     * @param keyword  关键词（搜索 doc_title），可为 null
     * @param category 文档分类，可为 null
     * @param status   状态过滤，可为 null
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<DocInfo> listDocuments(String keyword, String category, String status,
                                             int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        long total = docInfoMapper.countPage(keyword, category, status);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        List<DocInfo> records = docInfoMapper.selectPage(keyword, category, status, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 查询文档详情。
     *
     * @param id 文档ID
     * @return 文档实体
     * @throws BizException 文档不存在（PORTAL-40401）
     */
    public DocInfo getDocument(String id) {
        DocInfo doc = docInfoMapper.selectById(id);
        if (doc == null) {
            throw new BizException(
                    PortalErrorCode.DOC_NOT_FOUND.getCode(),
                    PortalErrorCode.DOC_NOT_FOUND.getMessage()
            );
        }
        return doc;
    }

    /**
     * 获取文档下载链接。
     * 通过 FileApi 获取 MinIO 预签名下载 URL。
     *
     * @param id 文档ID
     * @return 预签名下载 URL
     * @throws BizException 文档不存在（PORTAL-40401）
     */
    public String getDownloadUrl(String id) {
        DocInfo doc = getDocument(id);
        return fileApi.getDownloadUrl(doc.getFileObjectId());
    }

    /**
     * 新增文档。
     * 生成 UUID 主键，设置创建人并插入数据库。
     *
     * @param req 新增请求
     * @return 新创建的文档实体
     */
    @Transactional(rollbackFor = Exception.class)
    public DocInfo createDocument(DocumentCreateReqDTO req) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        String docId = UUID.randomUUID().toString().replace("-", "");

        DocInfo entity = new DocInfo();
        entity.setId(docId);
        entity.setDocTitle(req.getDocTitle());
        entity.setDocCategory(req.getDocCategory());
        entity.setFileObjectId(req.getFileObjectId());
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(currentEmpId);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedBy(currentEmpId);
        entity.setUpdatedTime(LocalDateTime.now());

        docInfoMapper.insert(entity);
        log.info("[DocService.createDocument] 新增文档成功, id={}, title={}", docId, req.getDocTitle());
        return entity;
    }

    /**
     * 更新文档。
     * 查询文档是否存在，不存在则抛异常；存在则动态更新非 null 字段。
     *
     * @param id  文档ID
     * @param req 更新请求
     * @throws BizException 文档不存在（PORTAL-40401）
     */
    public void updateDocument(String id, DocumentUpdateReqDTO req) {
        DocInfo existing = docInfoMapper.selectById(id);
        if (existing == null) {
            throw new BizException(
                    PortalErrorCode.DOC_NOT_FOUND.getCode(),
                    PortalErrorCode.DOC_NOT_FOUND.getMessage()
            );
        }

        String currentEmpId = currentUserApi.getCurrentEmpId();

        DocInfo patch = new DocInfo();
        patch.setId(id);
        patch.setDocTitle(req.getDocTitle());
        patch.setDocCategory(req.getDocCategory());
        patch.setFileObjectId(req.getFileObjectId());
        patch.setStatus(req.getStatus());
        patch.setUpdatedBy(currentEmpId);

        docInfoMapper.updateById(patch);
        log.info("[DocService.updateDocument] 更新文档成功, id={}", id);
    }

    /**
     * 逻辑删除文档（设置 status=DISABLED）。
     *
     * @param id 文档ID
     * @throws BizException 文档不存在（PORTAL-40401）
     */
    public void deleteDocument(String id) {
        DocInfo existing = docInfoMapper.selectById(id);
        if (existing == null) {
            throw new BizException(
                    PortalErrorCode.DOC_NOT_FOUND.getCode(),
                    PortalErrorCode.DOC_NOT_FOUND.getMessage()
            );
        }

        String currentEmpId = currentUserApi.getCurrentEmpId();
        docInfoMapper.softDeleteById(id, currentEmpId);
        log.info("[DocService.deleteDocument] 逻辑删除文档成功, id={}", id);
    }

    /**
     * 按分类查询启用状态的文档列表。
     *
     * @param category 文档分类
     * @return 启用状态的文档列表
     */
    public List<DocInfo> listActiveByCategory(String category) {
        return docInfoMapper.listActiveByCategory(category);
    }
}
