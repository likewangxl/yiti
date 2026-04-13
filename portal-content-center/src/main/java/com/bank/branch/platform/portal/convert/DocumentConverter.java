package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.DocumentDTO;
import com.bank.branch.platform.portal.entity.DocInfo;

/**
 * DocInfo Entity -> DTO 转换器
 *
 * <p>纯静态方法，无业务逻辑。docCategoryDesc / fileName 留 null，
 * 由 Service 层通过 DictApi / FileApi 填充。</p>
 */
public final class DocumentConverter {

    private DocumentConverter() {}

    /**
     * Entity -> DocumentDTO
     *
     * <p>docCategoryDesc 留 null，由 Service 层填充。
     * fileName 留 null，需要通过 FileApi 查询。</p>
     *
     * @param entity 文档实体
     * @return DocumentDTO，entity 为 null 时返回 null
     */
    public static DocumentDTO toDTO(DocInfo entity) {
        if (entity == null) return null;
        return DocumentDTO.builder()
                .id(entity.getId())
                .docTitle(entity.getDocTitle())
                .docCategory(entity.getDocCategory())
                .docCategoryDesc(null) // 需要 DictApi 翻译，Service 层填充
                .fileObjectId(entity.getFileObjectId())
                .fileName(null) // 需要 FileApi 查询，Service 层填充
                .status(entity.getStatus())
                .updatedTime(entity.getUpdatedTime())
                .build();
    }
}
