package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.portal.api.DocumentApi;
import com.bank.branch.platform.portal.api.dto.DocumentDTO;
import com.bank.branch.platform.portal.convert.DocumentConverter;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.mapper.DocInfoMapper;
import com.bank.branch.platform.portal.service.DocService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档 Facade 实现
 *
 * <p>实现 {@link DocumentApi} 接口，负责将 DocService 返回的实体
 * 转换为跨模块 DTO。所有方法均为只读查询，不提供写操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentFacade implements DocumentApi {

    private final DocService docService;
    private final DocInfoMapper docInfoMapper;

    /**
     * 按 ID 查询文档。
     * 查询不到返回 empty，不抛异常。
     *
     * @param docId 文档ID
     * @return 文档 DTO（Optional）
     */
    @Override
    public Optional<DocumentDTO> getDocument(String docId) {
        DocInfo entity = docInfoMapper.selectById(docId);
        return Optional.ofNullable(DocumentConverter.toDTO(entity));
    }

    /**
     * 按分类查询启用状态的文档列表。
     * 委托 DocService 查询后转换为 DocumentDTO。
     *
     * @param category 文档分类
     * @return 启用状态的文档 DTO 列表
     */
    @Override
    public List<DocumentDTO> listDocumentsByCategory(String category) {
        List<DocInfo> entities = docService.listActiveByCategory(category);
        return entities.stream()
                .map(DocumentConverter::toDTO)
                .collect(Collectors.toList());
    }
}
