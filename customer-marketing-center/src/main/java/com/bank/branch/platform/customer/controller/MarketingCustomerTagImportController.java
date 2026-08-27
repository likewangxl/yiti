package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.tag.TagImportCreateRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportBatch;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagImportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 页面五追加/全量替换导入批次接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/marketing/customer-tag-import-batches")
public class MarketingCustomerTagImportController {

    private final MarketingCustomerTagImportService importService;
    private final CurrentUserApi currentUserApi;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @BizAuth(bizType = BizType.TAG, action = BizAction.IMPORT)
    public ResponseWrapper<?> preview(@RequestPart("file") MultipartFile file,
                                      @RequestParam Long tagId,
                                      @RequestParam String importMode) {
        return ResponseWrapper.success(importService.preview(file, tagId, importMode));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @BizAuth(bizType = BizType.TAG, action = BizAction.IMPORT)
    @AuditLog(action = "CREATE_CUSTOMER_TAG_IMPORT_BATCH", resourceType = "TAG")
    public ResponseWrapper<MarketingCustomerTagImportBatch> create(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String tagCategory,
            @RequestParam(required = false) String tagType,
            @RequestParam(required = false) Integer tagPriority,
            @RequestParam(required = false) String tagDescription,
            @RequestParam String importMode) {
        TagImportCreateRequest request = new TagImportCreateRequest();
        request.setTagId(tagId);
        request.setTagName(tagName);
        request.setTagCategory(tagCategory);
        request.setTagType(tagType);
        request.setTagPriority(tagPriority);
        request.setTagDescription(tagDescription);
        request.setImportMode(importMode);
        return ResponseWrapper.success(importService.create(file, request,
                currentUserApi.getCurrentEmpId(), currentUserApi.getCurrentOrgCode()));
    }

    @GetMapping
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    public ResponseWrapper<?> list(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Long tagId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") int pageNo,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        String empId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.page(importService.list(keyword, tagId, status,
                currentUserApi.isSystemAdmin() ? null : empId, pageNo, pageSize));
    }

    @GetMapping("/{batchId}")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    public ResponseWrapper<?> detail(@PathVariable Long batchId) {
        return ResponseWrapper.success(importService.get(batchId, currentUserApi.getCurrentEmpId(),
                currentUserApi.isSystemAdmin()));
    }

    @GetMapping("/{batchId}/details")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    public ResponseWrapper<?> details(@PathVariable Long batchId,
                                      @RequestParam(required = false) String approvalStatus,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int pageNo,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(importService.details(batchId, approvalStatus, keyword, pageNo, pageSize,
                currentUserApi.getCurrentEmpId(), currentUserApi.isSystemAdmin()));
    }

    @PostMapping("/{batchId}/cancel")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @AuditLog(action = "CANCEL_CUSTOMER_TAG_IMPORT_BATCH", resourceType = "TAG")
    public ResponseWrapper<Void> cancel(@PathVariable Long batchId) {
        importService.cancel(batchId, currentUserApi.getCurrentEmpId(), currentUserApi.isSystemAdmin());
        return ResponseWrapper.success();
    }

    @GetMapping("/{batchId}/source-file")
    @BizAuth(bizType = BizType.TAG, action = BizAction.EXPORT)
    @AuditLog(action = "DOWNLOAD_CUSTOMER_TAG_IMPORT_SOURCE", resourceType = "TAG")
    public void sourceFile(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        String empId = currentUserApi.getCurrentEmpId();
        MarketingCustomerTagImportBatch batch = importService.get(batchId, empId, currentUserApi.isSystemAdmin());
        byte[] bytes = importService.sourceFile(batchId, empId, currentUserApi.isSystemAdmin());
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(batch.getSourceFileName(), StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }

    /** 下载与当前标签导入解析器一致的通用模板，不绑定具体标签。 */
    @GetMapping("/import-template")
    @BizAuth(bizType = BizType.TAG, action = BizAction.IMPORT)
    @AuditLog(action = "DOWNLOAD_CUSTOMER_TAG_IMPORT_TEMPLATE", resourceType = "TAG")
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode("营销客户标签导入模板.xlsx", StandardCharsets.UTF_8));
        response.getOutputStream().write(importService.importTemplate());
    }
}
