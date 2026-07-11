package com.bank.branch.platform.performance.eval.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportAcceptedDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.service.EvalAssignAdminService;
import com.bank.branch.platform.performance.eval.service.EvalRewardAdminService;
import com.bank.branch.platform.performance.eval.service.EvalRewardImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 奖励分配（REWARD）管理端控制器.
 * <p>模板下载、Excel 导入（异步受理）、批次列表/详情、发布、导出。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/reward")
@Tag(name = "Eval Reward", description = "奖励分配导入（管理端）")
@RequiredArgsConstructor
public class EvalRewardAdminController {

    private final EvalRewardImportService importService;
    private final EvalRewardAdminService adminService;
    /** 复用通用 publishBatch（草稿 2→ACTIVE 0，仅改批次状态）。 */
    private final EvalAssignAdminService assignAdminService;
    private final CurrentUserApi currentUserApi;

    /** 下载奖励分配导入模板（8 列）。 */
    @GetMapping("/import-template")
    @Operation(summary = "下载奖励分配导入模板")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("奖励分配导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        EvalRewardImportRow sample = new EvalRewardImportRow();
        sample.setBeAssignedUserId("100002");
        sample.setBeAssignedUserName("张三");
        sample.setDeptName("信贷部");
        sample.setOriginalValue(new BigDecimal("76.5"));
        sample.setAssignValueIgnored("");
        sample.setCashValue(new BigDecimal("80"));
        sample.setAssignUserId("100001");
        sample.setAssignTotal(new BigDecimal("100"));
        EasyExcel.write(response.getOutputStream(), EvalRewardImportRow.class)
                .sheet("奖励分配")
                .doWrite(List.of(sample));
    }

    /**
     * 导入奖励分配（Excel，异步受理）。
     *
     * @param file     上传的 .xlsx 文件
     * @param taskName 任务名称（必填）
     * @param deadline 分配截止时间（yyyy-MM-dd HH:mm:ss）
     * @return 受理结果（batchId + status=3 处理中）
     */
    @PostMapping("/import")
    @Operation(summary = "导入奖励分配（异步受理）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public ResponseWrapper<EvalAssignImportAcceptedDTO> importExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam("taskName") String taskName,
            @RequestParam("deadline")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline) {
        String createBy = currentUserApi.getCurrentEmpId();
        log.info("[EvalRewardAdminController.importExcel] fileName={}, taskName={}, deadline={}, createBy={}",
                file != null ? file.getOriginalFilename() : null, taskName, deadline, createBy);
        List<EvalRewardImportRow> rows = importService.parseRows(file);
        Long batchId = importService.createImportingBatch(taskName, deadline, createBy);
        importService.processImport(batchId, rows, taskName, deadline, createBy);
        return ResponseWrapper.success(new EvalAssignImportAcceptedDTO(batchId, 3));
    }

    /** 分页查询 REWARD 批次列表。 */
    @GetMapping("/batches")
    @Operation(summary = "奖励分配批次列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalAssignBatch>> batches(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseWrapper.success(adminService.pageBatches(status, keyword, page, pageSize));
    }

    /** 批次详情（含分页明细）。 */
    @GetMapping("/batches/{batchId}")
    @Operation(summary = "奖励分配批次详情")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<Map<String, Object>> batchDetail(
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseWrapper.success(adminService.getBatchDetail(batchId, page, pageSize));
    }

    /** 发布草稿批次 → ACTIVE（复用通用 publishBatch）。 */
    @PostMapping("/batches/{batchId}/publish")
    @Operation(summary = "发布奖励分配批次")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<EvalAssignBatch> publish(@PathVariable("batchId") Long batchId) {
        return ResponseWrapper.success(assignAdminService.publishBatch(batchId));
    }

    /** 导出批次明细 Excel。 */
    @GetMapping("/batches/{batchId}/export")
    @Operation(summary = "导出奖励分配批次明细")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.EXPORT)
    public void export(@PathVariable("batchId") Long batchId, HttpServletResponse response) throws IOException {
        byte[] data = adminService.exportItems(batchId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("奖励分配明细.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(data);
    }
}
