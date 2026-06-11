package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.service.EvalAssignAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 待处理任务批次管理端控制器.
 * <p>提供导入批次的分页列表、详情查看（含分页明细）、草稿发布、Excel 导出。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/assign/batches")
@Tag(name = "Eval Assign Batch", description = "待处理任务批次管理")
@RequiredArgsConstructor
public class EvalAssignBatchController {

    private final EvalAssignAdminService evalAssignAdminService;

    /**
     * 分页查询导入批次列表。
     *
     * @param status   批次状态（null=全部，0=ACTIVE，1=CLOSED，2=DRAFT）
     * @param keyword  关键词（匹配批次ID/创建人）
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 分页批次列表（含 itemCount）
     */
    @GetMapping
    @Operation(summary = "分页查询导入批次列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalAssignBatch>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[EvalAssignBatchController.list] status={}, keyword={}, page={}, pageSize={}",
                status, keyword, page, pageSize);
        return ResponseWrapper.success(evalAssignAdminService.pageBatches(status, keyword, page, pageSize));
    }

    /**
     * 查询批次详情（含分页明细）。
     *
     * @param batchId  批次ID
     * @param page     明细页码，默认 1
     * @param pageSize 明细每页条数，默认 50，最大 200
     * @return Map 含 batch 和 items（分页）
     */
    @GetMapping("/{batchId}")
    @Operation(summary = "查询批次详情及分页明细")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<Map<String, Object>> detail(
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "50") @Min(1) @Max(200) int pageSize) {
        log.debug("[EvalAssignBatchController.detail] batchId={}, page={}, pageSize={}", batchId, page, pageSize);
        return ResponseWrapper.success(evalAssignAdminService.getBatchDetail(batchId, page, pageSize));
    }

    /**
     * 确认发布草稿批次 → ACTIVE。
     *
     * @param batchId 批次ID
     * @return 更新后的批次
     */
    @PostMapping("/{batchId}/publish")
    @Operation(summary = "确认发布草稿批次")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<EvalAssignBatch> publish(@PathVariable("batchId") Long batchId) {
        log.info("[EvalAssignBatchController.publish] batchId={}", batchId);
        return ResponseWrapper.success(evalAssignAdminService.publishBatch(batchId));
    }

    /**
     * 导出批次全部明细为 Excel。
     *
     * @param batchId 批次ID
     */
    @GetMapping("/{batchId}/export")
    @Operation(summary = "导出批次明细 Excel")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.EXPORT)
    public void export(@PathVariable("batchId") Long batchId, HttpServletResponse response) throws IOException {
        log.info("[EvalAssignBatchController.export] batchId={}", batchId);
        byte[] data = evalAssignAdminService.exportItems(batchId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("评价明细_batch_" + batchId + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(data);
    }
}
