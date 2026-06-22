package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.api.dto.DocumentDTO;
import com.bank.branch.platform.portal.convert.DocumentConverter;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.service.DocService;
import com.bank.branch.platform.governance.api.FileApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文档公开只读 REST Controller (E.1-E.2)
 *
 * <p>提供文档列表查询和下载接口，供前端门户页面调用。</p>
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;
    private final FileApi fileApi;

    /**
     * E.1 文档列表（分页）
     *
     * @param keyword  关键词模糊搜索（可选）
     * @param category 分类过滤（可选）
     * @param status   状态过滤（可选）
     * @param pageNo   页码（默认 1）
     * @param pageSize 每页条数（默认 20）
     * @return 分页后的文档列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.DOC, action = BizAction.LIST)
    public ResponseWrapper<DocumentDTO> listDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int pageNo,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        PageResult<DocInfo> entityPage = docService.listDocuments(keyword, category, status, pageNo, pageSize);
        // 批量取本页文档关联文件的大小（避免逐行 N+1），再带进 DTO
        List<String> fileIds = entityPage.getRecords().stream()
                .map(DocInfo::getFileObjectId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Long> sizeMap = fileIds.isEmpty() ? Map.of() : fileApi.getFileSizes(fileIds);
        List<DocumentDTO> dtos = entityPage.getRecords().stream()
                .map(e -> DocumentConverter.toDTO(e, sizeMap.get(e.getFileObjectId())))
                .collect(Collectors.toList());
        return ResponseWrapper.page(PageResult.of(
                entityPage.getPageNo(), entityPage.getPageSize(),
                entityPage.getTotal(), dtos));
    }

    /**
     * E.2 下载文档（返回 MinIO 预签名下载 URL）
     *
     * @param id 文档ID
     * @return 预签名下载 URL
     */
    @GetMapping("/{id}/download")
    @BizAuth(bizType = BizType.DOC, action = BizAction.READ)
    public ResponseWrapper<String> downloadDocument(@PathVariable String id) {
        String downloadUrl = docService.getDownloadUrl(id);
        return ResponseWrapper.success(downloadUrl);
    }
}
