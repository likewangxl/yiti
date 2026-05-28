package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.service.EvalTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 评价标签管理控制器.
 * <p>提供评价标签字典的增删改查接口，供内部评价模块使用。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/tags")
@Tag(name = "Eval Tag", description = "评价标签字典管理")
@Validated
@RequiredArgsConstructor
public class EvalTagController {

    private final EvalTagService evalTagService;

    /**
     * 分页查询标签列表.
     *
     * @param tagType  标签类型（1=被评价人, 2=评价人，不传则全部）
     * @param keyword  名称关键词（可选）
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询标签列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalTag>> list(
            @RequestParam(value = "tagType", required = false) Integer tagType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[EvalTagController.list] tagType={}, keyword={}, page={}, pageSize={}", tagType, keyword, page, pageSize);
        return ResponseWrapper.success(evalTagService.list(tagType, keyword, page, pageSize));
    }

    /**
     * 新建评价标签.
     *
     * @param tagName 标签名称（必填，不能为空白）
     * @param tagType 标签类型（必填，1=被评价人, 2=评价人）
     * @return 创建后的标签实体
     */
    @PostMapping
    @Operation(summary = "新建标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<EvalTag> create(
            @RequestParam("tagName") @NotBlank String tagName,
            @RequestParam("tagType") @NotNull Integer tagType) {
        log.info("[EvalTagController.create] tagName={}, tagType={}", tagName, tagType);
        return ResponseWrapper.success(evalTagService.create(tagName, tagType));
    }

    /**
     * 编辑评价标签.
     *
     * @param tagId   标签ID（路径参数）
     * @param tagName 新名称（可选）
     * @param status  新状态（可选，1=启用, 0=停用）
     * @return 空成功响应
     */
    @PutMapping("/{tagId}")
    @Operation(summary = "编辑标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> update(
            @PathVariable("tagId") Long tagId,
            @RequestParam(value = "tagName", required = false) String tagName,
            @RequestParam(value = "status", required = false) Integer status) {
        log.info("[EvalTagController.update] tagId={}, tagName={}, status={}", tagId, tagName, status);
        evalTagService.update(tagId, tagName, status);
        return ResponseWrapper.success();
    }

    /**
     * 删除评价标签.
     *
     * @param tagId 标签ID（路径参数）
     * @return 空成功响应
     */
    @DeleteMapping("/{tagId}")
    @Operation(summary = "删除标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.DELETE)
    public ResponseWrapper<Void> delete(@PathVariable("tagId") Long tagId) {
        log.info("[EvalTagController.delete] tagId={}", tagId);
        evalTagService.delete(tagId);
        return ResponseWrapper.success();
    }
}
