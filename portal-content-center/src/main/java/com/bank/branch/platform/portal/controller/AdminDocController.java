package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.controller.dto.doc.DocumentCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.doc.DocumentUpdateReqDTO;
import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.service.DocService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档管理端 REST Controller (E.3-E.5)
 *
 * <p>提供文档的新增、编辑和逻辑删除接口。
 * 所有接口需要 DOC + WRITE 业务权限。</p>
 */
@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
public class AdminDocController {

    private final DocService docService;

    /**
     * E.3 新增文档
     *
     * @param req 新增请求
     * @return 新创建的文档 ID
     */
    @PostMapping
    @BizAuth(bizType = BizType.DOC, action = BizAction.WRITE)
    public ResponseWrapper<String> createDocument(@Valid @RequestBody DocumentCreateReqDTO req) {
        DocInfo doc = docService.createDocument(req);
        return ResponseWrapper.success(doc.getId());
    }

    /**
     * E.4 编辑文档
     *
     * @param id  文档ID
     * @param req 更新请求
     * @return 空成功响应
     */
    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.DOC, action = BizAction.WRITE)
    public ResponseWrapper<Void> updateDocument(
            @PathVariable String id,
            @Valid @RequestBody DocumentUpdateReqDTO req) {
        docService.updateDocument(id, req);
        return ResponseWrapper.success(null);
    }

    /**
     * E.5 删除文档（逻辑删除）
     *
     * @param id 文档ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.DOC, action = BizAction.WRITE)
    public ResponseWrapper<Void> deleteDocument(@PathVariable String id) {
        docService.deleteDocument(id);
        return ResponseWrapper.success(null);
    }
}
