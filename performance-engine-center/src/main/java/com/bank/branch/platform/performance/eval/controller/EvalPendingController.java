package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalPendingItemDTO;
import com.bank.branch.platform.performance.eval.service.EvalAssignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 待处理任务（用户端）控制器.
 * <p>当前登录人作为打分人：按被打分人部门查看待处理汇总、查看某部门明细、逐人提交打分。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval/pending-tasks")
@Tag(name = "Eval Pending", description = "待处理任务（用户端）")
@Validated
@RequiredArgsConstructor
public class EvalPendingController {

    private final EvalAssignService evalAssignService;
    private final CurrentUserApi currentUserApi;

    /**
     * 我的待处理任务汇总（按被打分人部门聚合未提交明细）。
     */
    @GetMapping
    @Operation(summary = "我的待处理任务汇总")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<List<EvalPendingGroupDTO>> myPending() {
        String evalUserId = currentUserApi.getCurrentEmpId();
        log.debug("[EvalPendingController.myPending] evalUserId={}", evalUserId);
        return ResponseWrapper.success(evalAssignService.listMyPendingGroups(evalUserId));
    }

    /**
     * 处理某部门：查询该批次+部门下分配给我的明细。
     *
     * @param batchId 批次ID
     * @param dept    被打分人部门（可空，空串表示无部门）
     */
    @GetMapping("/items")
    @Operation(summary = "待处理任务明细")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<List<EvalPendingItemDTO>> items(
            @RequestParam("batchId") Long batchId,
            @RequestParam(value = "dept", required = false, defaultValue = "") String dept) {
        String evalUserId = currentUserApi.getCurrentEmpId();
        log.debug("[EvalPendingController.items] evalUserId={}, batchId={}, dept={}", evalUserId, batchId, dept);
        return ResponseWrapper.success(evalAssignService.listMyPendingItems(evalUserId, batchId, dept));
    }

    /**
     * 提交某条明细的打分。
     */
    @PostMapping("/submit")
    @Operation(summary = "提交待处理任务打分")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> submit(@RequestBody @Valid SubmitReq req) {
        String evalUserId = currentUserApi.getCurrentEmpId();
        log.info("[EvalPendingController.submit] evalUserId={}, itemId={}, score={}",
                evalUserId, req.getItemId(), req.getScore());
        evalAssignService.submitScore(evalUserId, req.getItemId(), req.getScore());
        return ResponseWrapper.success();
    }

    /** 提交打分请求体。 */
    @Data
    public static class SubmitReq {
        /** 明细ID（必填）。 */
        @NotNull
        private Long itemId;
        /** 分数（必填；数值 10~100 或等级预设值，由 service 按评价类型校验）。 */
        @NotNull
        private Integer score;
    }
}
