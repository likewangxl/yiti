package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadImportConfirmRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadImportPreviewResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadImportQuery;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 页面三批量导入记录、明细、确认和导入文件下载接口。 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/marketing/lead-import-batches")
@Tag(name = "营销线索批量导入")
public class MarketingLeadImportController {

    private final MarketingLeadImportService service;
    private final CurrentUserApi currentUserApi;

    @GetMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "查询线索导入批次")
    public ResponseWrapper<MarketingLeadImportBatch> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        LeadImportQuery query = new LeadImportQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ResponseWrapper.page(service.list(query, currentUserApi.getCurrentEmpId(),
                currentUserApi.isSystemAdmin()));
    }

    @PostMapping("/preview")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.IMPORT)
    @Operation(summary = "预览线索导入文件")
    public ResponseWrapper<LeadImportPreviewResponse> preview(
            @RequestPart("file") MultipartFile file) {
        return ResponseWrapper.success(service.preview(file, currentUserApi.getCurrentEmpId(),
                currentUserApi.getCurrentOrgCode()));
    }

    @PostMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.IMPORT)
    @AuditLog(action = "CREATE_MARKETING_LEAD_IMPORT_BATCH", resourceType = "LEAD")
    @Operation(summary = "创建并处理线索导入批次")
    public ResponseWrapper<LeadImportPreviewResponse> create(
            @RequestPart("file") MultipartFile file) {
        return ResponseWrapper.success(service.create(file, currentUserApi.getCurrentEmpId(),
                currentUserApi.getCurrentOrgCode()));
    }

    @GetMapping("/{batchId}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询导入批次汇总")
    public ResponseWrapper<MarketingLeadImportBatch> detail(@PathVariable Long batchId) {
        return ResponseWrapper.success(service.getBatch(batchId, currentUserApi.getCurrentEmpId(),
                currentUserApi.isSystemAdmin()));
    }

    @GetMapping("/{batchId}/details")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询导入明细，失败优先")
    public ResponseWrapper<MarketingLeadImportDetail> details(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<MarketingLeadImportDetail> result = service.listDetails(batchId, pageNo, pageSize,
                currentUserApi.getCurrentEmpId(), currentUserApi.isSystemAdmin());
        return ResponseWrapper.page(result);
    }

    @PostMapping("/{batchId}/confirm")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.IMPORT)
    @AuditLog(action = "CONFIRM_MARKETING_LEAD_IMPORT_BATCH", resourceType = "LEAD")
    @Operation(summary = "确认处理正常导入行或放弃重传")
    public ResponseWrapper<MarketingLeadImportBatch> confirm(
            @PathVariable Long batchId,
            @Valid @RequestBody LeadImportConfirmRequest request) {
        return ResponseWrapper.success(service.confirm(batchId, request.getAction(),
                currentUserApi.getCurrentEmpId(), request.getRemark(), currentUserApi.isSystemAdmin()));
    }

    @GetMapping("/{batchId}/source-file")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @AuditLog(action = "DOWNLOAD_MARKETING_LEAD_IMPORT_SOURCE", resourceType = "LEAD")
    @Operation(summary = "下载导入原文件")
    public void sourceFile(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        write(response, service.sourceFile(batchId, currentUserApi.getCurrentEmpId(),
                currentUserApi.isSystemAdmin()), "application/octet-stream");
    }

    @GetMapping("/{batchId}/error-file")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @AuditLog(action = "DOWNLOAD_MARKETING_LEAD_IMPORT_ERRORS", resourceType = "LEAD")
    @Operation(summary = "下载导入失败明细")
    public void errorFile(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        write(response, service.errorFile(batchId, currentUserApi.getCurrentEmpId(),
                currentUserApi.isSystemAdmin()), "text/csv;charset=UTF-8");
    }

    private void write(HttpServletResponse response, byte[] bytes, String contentType) throws IOException {
        response.setContentType(contentType);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
