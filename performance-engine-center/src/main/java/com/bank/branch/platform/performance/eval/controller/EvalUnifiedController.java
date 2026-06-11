package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.UnifiedEvalTaskRow;
import com.bank.branch.platform.performance.eval.service.EvalUnifiedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一评价任务列表 —— 合并规则任务(EVAL_TASK)与导入批次(EVAL_ASSIGN_BATCH)。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/tasks/unified")
@Tag(name = "Eval Task Unified", description = "统一评价任务列表")
@RequiredArgsConstructor
public class EvalUnifiedController {

    private final EvalUnifiedService unifiedService;

    @GetMapping
    @Operation(summary = "分页查询统一评价任务列表（含规则任务 + 导入批次）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<UnifiedEvalTaskRow>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[EvalUnifiedController.list] status={}, keyword={}, page={}, pageSize={}",
                status, keyword, page, pageSize);
        return ResponseWrapper.success(unifiedService.listUnified(status, keyword, page, pageSize));
    }

    @DeleteMapping("/{sourceType}/{sourceId}")
    @Operation(summary = "删除评价任务（硬删除，需截止时间已过）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.DELETE)
    public ResponseWrapper<String> delete(
            @PathVariable String sourceType,
            @PathVariable Long sourceId) {
        log.info("[EvalUnifiedController.delete] sourceType={} sourceId={}", sourceType, sourceId);
        unifiedService.deleteUnified(sourceType, sourceId);
        return ResponseWrapper.success("已删除");
    }
}
