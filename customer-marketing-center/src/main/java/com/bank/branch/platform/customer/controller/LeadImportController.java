package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.LeadImportExecuteReqDTO;
import com.bank.branch.platform.customer.dto.resp.LeadImportPreviewResp;
import com.bank.branch.platform.customer.entity.LeadImportBatch;
import com.bank.branch.platform.customer.service.LeadImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 线索批量导入 REST 控制器。
 * <p>
 * 提供线索导入预览、执行导入和导入批次列表查询功能。
 * 所有操作需要 {@link BizAction#IMPORT} 权限。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leads")
@Validated
@Tag(name = "线索导入")
public class LeadImportController {

    private final LeadImportService leadImportService;
    private final CurrentUserApi currentUserApi;

    /**
     * 导入预览：解析文件行数和基本校验，返回批次ID供后续执行。
     * <p>
     * 不写入 cust_lead 数据，仅创建 lead_import_batch 记录。
     * </p>
     *
     * @param file 导入文件（CSV/XLSX）
     * @return 预览结果（批次ID + 行数统计）
     */
    @PostMapping("/import/preview")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.IMPORT)
    @Operation(summary = "线索导入预览")
    public ResponseWrapper<LeadImportPreviewResp> preview(
            @RequestPart("file") MultipartFile file) {
        log.info("[LeadImportController.preview] fileName={}", file.getOriginalFilename());
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        LeadImportPreviewResp resp = leadImportService.preview(file, empId, orgCode);
        return ResponseWrapper.success(resp);
    }

    /**
     * 执行导入：基于预览创建的批次ID，提交导入请求。
     *
     * @param req 执行导入请求 DTO（包含 batchId）
     * @return 操作结果
     */
    @PostMapping("/import/execute")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.IMPORT)
    @Operation(summary = "执行线索导入")
    public ResponseWrapper<Void> execute(@Valid @RequestBody LeadImportExecuteReqDTO req) {
        log.info("[LeadImportController.execute] batchId={}", req.getBatchId());
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        leadImportService.execute(req.getBatchId(), empId, orgCode);
        return ResponseWrapper.success();
    }

    /**
     * 分页查询导入批次列表。
     *
     * @param keyword  关键词（模糊匹配批次号/文件名）
     * @param status   批次状态过滤
     * @param pageNo   页码，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 分页批次列表
     */
    @GetMapping("/batches")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "导入批次列表")
    public ResponseWrapper<LeadImportBatch> listBatches(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[LeadImportController.listBatches] keyword={}, status={}, pageNo={}, pageSize={}",
                keyword, status, pageNo, pageSize);
        PageResult<LeadImportBatch> result = leadImportService.listBatches(keyword, status, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
