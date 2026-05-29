package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
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

    @GetMapping("/page")
    @Operation(summary = "分页查询人员标签列表（含部门/岗位/角色）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalUserRoleRowDTO>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[EvalUserTagController.page] keyword={}, page={}, pageSize={}", keyword, page, pageSize);
        return ResponseWrapper.success(evalUserTagService.pageUserRoles(keyword, page, pageSize));
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "覆盖式保存人员评价角色（被评价单选/评价人多选）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> saveRoles(@PathVariable("userId") Long userId,
                                           @Validated @RequestBody SaveRolesReq req) {
        log.info("[EvalUserTagController.saveRoles] userId={}, beEvalTagId={}, evalTagIds={}",
                userId, req.getBeEvalTagId(), req.getEvalTagIds());
        evalUserTagService.saveUserRoles(userId, req.getBeEvalTagId(), req.getEvalTagIds());
        return ResponseWrapper.success();
    }

    @Data
    public static class BindReq {
        @NotNull
        private Long userId;
        private List<Long> tagIds;
    }

    @Data
    public static class SaveRolesReq {
        /** 被评价人标签ID（null 表示清空被评价人角色）. */
        private Long beEvalTagId;
        /** 评价人标签ID列表（null/空 表示清空评价人角色）. */
        private List<Long> evalTagIds;
    }
}
