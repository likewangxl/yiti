package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.service.EvalUserTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 人员标签关联管理控制器.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/user-tags")
@Tag(name = "Eval User Tag", description = "人员标签关联管理")
@Validated
@RequiredArgsConstructor
public class EvalUserTagController {

    private final EvalUserTagService evalUserTagService;

    @GetMapping
    @Operation(summary = "查询人员标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<List<EvalUserTag>> list(@RequestParam("userId") Long userId) {
        log.debug("[EvalUserTagController.list] userId={}", userId);
        return ResponseWrapper.success(evalUserTagService.getByUserId(userId));
    }

    @PostMapping
    @Operation(summary = "批量绑定人员标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> bind(@Validated @RequestBody BindReq req) {
        log.info("[EvalUserTagController.bind] userId={}, tagIds={}", req.getUserId(), req.getTagIds());
        evalUserTagService.batchBind(req.getUserId(), req.getTagIds());
        return ResponseWrapper.success();
    }

    @DeleteMapping
    @Operation(summary = "批量解绑人员标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.DELETE)
    public ResponseWrapper<Void> unbind(@Validated @RequestBody BindReq req) {
        log.info("[EvalUserTagController.unbind] userId={}, tagIds={}", req.getUserId(), req.getTagIds());
        evalUserTagService.batchUnbind(req.getUserId(), req.getTagIds());
        return ResponseWrapper.success();
    }

    @Data
    public static class BindReq {
        @NotNull
        private Long userId;
        private List<Long> tagIds;
    }
}
