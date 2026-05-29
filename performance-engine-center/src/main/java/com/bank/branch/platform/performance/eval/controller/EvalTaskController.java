package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.entity.EvalTaskTarget;
import com.bank.branch.platform.performance.eval.service.EvalTaskService;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评价任务管理控制器.
 * <p>提供评价任务的分页查询、详情查询、发起任务和手动关闭任务接口，供管理员使用。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/tasks")
@Tag(name = "Eval Task", description = "评价任务管理")
@Validated
@RequiredArgsConstructor
public class EvalTaskController {

    private final EvalTaskService evalTaskService;
    private final CurrentUserApi currentUserApi;

    // =============================================
    // 请求 DTO
    // =============================================

    /**
     * 发起评价任务请求体.
     */
    @Data
    public static class CreateTaskReq {
        /** 任务名称（必填）. */
        @NotBlank
        private String taskName;
        /** 截止时间（必须晚于当前时间）. */
        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;
        /** 被评价人工号列表（必填）. */
        @NotNull
        private List<String> beEvalUserIds;
    }

    // =============================================
    // 端点
    // =============================================

    /**
     * 分页查询评价任务列表.
     *
     * @param status   任务状态（0=进行中，1=已结束，不传则全部）
     * @param keyword  任务名称关键词（可选）
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询评价任务列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalTask>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[EvalTaskController.list] status={}, keyword={}, page={}, pageSize={}", status, keyword, page, pageSize);
        return ResponseWrapper.success(evalTaskService.list(status, keyword, page, pageSize));
    }

    /**
     * 查询评价任务详情及被评价人进度.
     *
     * @param taskId 任务ID（路径参数）
     * @return Map 含 "task"（主记录）和 "targets"（被评价人明细列表）
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情及进度")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<Map<String, Object>> getById(@PathVariable("taskId") Long taskId) {
        log.debug("[EvalTaskController.getById] taskId={}", taskId);
        EvalTask task = evalTaskService.getById(taskId);
        List<EvalTaskTarget> targets = evalTaskService.getTargetsByTaskId(taskId);
        Map<String, Object> result = Map.of("task", task, "targets", targets);
        return ResponseWrapper.success(result);
    }

    /**
     * 发起评价任务.
     *
     * <p>createBy 自动从当前登录用户上下文获取（{@link CurrentUserApi#getCurrentEmpId()}），
     * empId 在此处作为 Long 解析以兼容 USER_ID 为数值型的场景；若为字符串可视业务需要调整。</p>
     *
     * @param req 请求体（taskName、endTime、beEvalUserIds）
     * @return 创建后的任务实体
     */
    @PostMapping
    @Operation(summary = "发起评价任务")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<EvalTask> create(@RequestBody @Valid CreateTaskReq req) {
        String empIdStr = currentUserApi.getCurrentEmpId();
        String createBy = empIdStr;
        log.info("[EvalTaskController.create] taskName={}, endTime={}, beEvalUserIds={}, createBy={}",
                req.getTaskName(), req.getEndTime(), req.getBeEvalUserIds(), createBy);
        EvalTask task = evalTaskService.createTask(
                req.getTaskName(), req.getEndTime(), req.getBeEvalUserIds(), createBy);
        return ResponseWrapper.success(task);
    }

    /**
     * 手动关闭评价任务.
     *
     * <p>关闭后触发得分计算，任务状态更新为已结束（status=1）。</p>
     *
     * @param taskId 任务ID（路径参数）
     * @return 空成功响应
     */
    @PutMapping("/{taskId}/close")
    @Operation(summary = "手动关闭评价任务")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.EXECUTE)
    public ResponseWrapper<Void> close(@PathVariable("taskId") Long taskId) {
        log.info("[EvalTaskController.close] taskId={}", taskId);
        evalTaskService.closeTask(taskId);
        return ResponseWrapper.success();
    }
}
