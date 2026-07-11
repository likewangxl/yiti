package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingItemDTO;
import com.bank.branch.platform.performance.eval.dto.SubmitRewardBatchReq;
import com.bank.branch.platform.performance.eval.service.EvalRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 奖励分配（REWARD）用户端控制器.
 * <p>当前登录人作为分配人：按部门查看待分配汇总、查看某部门明细、一次性提交整组分配。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval/reward-tasks")
@Tag(name = "Eval Reward Pending", description = "奖励分配（用户端）")
@Validated
@RequiredArgsConstructor
public class EvalRewardPendingController {

    private final EvalRewardService rewardService;
    private final CurrentUserApi currentUserApi;

    /** 我的奖励分配待处理汇总（按部门聚合未提交明细）。 */
    @GetMapping
    @Operation(summary = "我的奖励分配待处理汇总")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<List<EvalRewardPendingGroupDTO>> myPending() {
        String assignUserId = currentUserApi.getCurrentEmpId();
        log.debug("[EvalRewardPendingController.myPending] assignUserId={}", assignUserId);
        return ResponseWrapper.success(rewardService.listMyRewardPendingGroups(assignUserId));
    }

    /**
     * 处理某部门：查询该批次+部门下分配给我的明细。
     *
     * @param batchId 批次ID
     * @param dept    部门名称（可空，空串表示无部门）
     */
    @GetMapping("/items")
    @Operation(summary = "奖励分配明细")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<List<EvalRewardPendingItemDTO>> items(
            @RequestParam("batchId") Long batchId,
            @RequestParam(value = "dept", required = false, defaultValue = "") String dept) {
        String assignUserId = currentUserApi.getCurrentEmpId();
        log.debug("[EvalRewardPendingController.items] assignUserId={}, batchId={}, dept={}", assignUserId, batchId, dept);
        return ResponseWrapper.success(rewardService.listMyRewardPendingItems(assignUserId, batchId, dept));
    }

    /** 一次性提交某部门下全部被分配人的分配值。 */
    @PostMapping("/submit-batch")
    @Operation(summary = "提交奖励分配")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> submit(@RequestBody @Valid SubmitRewardBatchReq req) {
        String assignUserId = currentUserApi.getCurrentEmpId();
        log.info("[EvalRewardPendingController.submit] assignUserId={}, batchId={}, dept={}, count={}",
                assignUserId, req.getBatchId(), req.getDept(), req.getItems().size());
        List<EvalRewardService.RewardEntry> entries = req.getItems().stream()
                .map(i -> new EvalRewardService.RewardEntry(i.getItemId(), i.getAssignValue()))
                .toList();
        rewardService.submitRewardBatch(assignUserId, req.getBatchId(), req.getDept(), entries);
        return ResponseWrapper.success();
    }
}
