package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.SubmitScoreReq;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.entity.EvalTaskTarget;
import com.bank.branch.platform.performance.eval.service.EvalScoreService;
import com.bank.branch.platform.performance.eval.service.EvalTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评价打分控制器（用户端）.
 * <p>提供当前登录用户查看待评价任务、查询待评价人员列表、提交打分的接口。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
@Tag(name = "Eval Score", description = "评价打分（用户端）")
@Validated
@RequiredArgsConstructor
public class EvalScoreController {

    private final EvalTaskService evalTaskService;
    private final EvalScoreService evalScoreService;
    private final CurrentUserApi currentUserApi;

    /**
     * 查询我的待评价任务列表.
     *
     * <p>暂返回所有进行中（status=0）的任务列表，后续可按评价人标签过滤。</p>
     *
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 进行中任务的分页结果
     */
    @GetMapping("/my-tasks")
    @Operation(summary = "我的待评价任务列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalTask>> myTasks(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[EvalScoreController.myTasks] page={}, pageSize={}", page, pageSize);
        // 暂返回所有进行中任务（status=0）
        return ResponseWrapper.success(evalTaskService.list(0, null, page, pageSize));
    }

    /**
     * 按任务查询待评价人员列表.
     *
     * <p>每条记录动态填充 scoreMode 字段，表示当前登录用户对该被评价人适用的评分方式
     * （1=数值打分, 2=等级打分），前端据此切换打分 UI。</p>
     *
     * @param taskId 任务ID（路径参数）
     * @return 该任务下的被评价人明细列表（含 scoreMode）
     */
    @GetMapping("/my-tasks/{taskId}/targets")
    @Operation(summary = "按任务查询待评价人员列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<List<EvalTaskTarget>> targets(@PathVariable("taskId") Long taskId) {
        String empIdStr = currentUserApi.getCurrentEmpId();
        String evalUserId = empIdStr;
        log.debug("[EvalScoreController.targets] taskId={} evalUserId={}", taskId, evalUserId);
        List<EvalTaskTarget> targets = evalTaskService.getTargetsByTaskId(taskId);
        for (EvalTaskTarget t : targets) {
            t.setScoreMode(evalScoreService.resolveScoreModeForUser(
                    t.getRuleId(), evalUserId, t.getBeEvalUserId()));
        }
        return ResponseWrapper.success(targets);
    }

    /**
     * 提交打分.
     *
     * <p>evalUserId 从当前登录用户上下文获取（{@link CurrentUserApi#getCurrentEmpId()}）。
     * 校验顺序：分数范围 → 任务状态 → target 归属 → 评价人权限 → 唯一性 → 写库。</p>
     *
     * @param req 请求体（taskId、targetId、score）
     * @return 空成功响应
     */
    @PostMapping("/scores")
    @Operation(summary = "提交打分")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> submitScore(@RequestBody @Valid SubmitScoreReq req) {
        String empIdStr = currentUserApi.getCurrentEmpId();
        String evalUserId = empIdStr;
        log.info("[EvalScoreController.submitScore] taskId={}, targetId={}, evalUserId={}, score={}",
                req.getTaskId(), req.getTargetId(), evalUserId, req.getScore());
        evalScoreService.submitScore(req.getTaskId(), req.getTargetId(), evalUserId, req.getScore());
        return ResponseWrapper.success();
    }
}
