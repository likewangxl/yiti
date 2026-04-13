package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.DocumentDTO;

import java.util.List;
import java.util.Optional;

/**
 * 文档对外接口。
 * 被其他模块依赖时，通过此接口获取文档数据。
 *
 * <p>所有方法均为只读查询，不提供写操作。</p>
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface DocumentApi {

    /**
     * 按 ID 查询文档。
     *
     * @param docId 文档ID
     * @return 文档 DTO（Optional，不存在时返回 empty）
     */
    Optional<DocumentDTO> getDocument(String docId);

    /**
     * 按分类查询启用状态的文档列表。
     *
     * @param category 文档分类
     * @return 启用状态的文档 DTO 列表
     */
    List<DocumentDTO> listDocumentsByCategory(String category);
}
