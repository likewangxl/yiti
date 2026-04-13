package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.TagCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.TagStatusReqDTO;
import com.bank.branch.platform.customer.dto.req.TagUpdateReqDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签管理 REST 控制器。
 * <p>
 * 提供标签的 CRUD、启禁用及列表查询接口。
 * 所有写操作需要 {@link BizAction#WRITE} 权限，列表查询需要 {@link BizAction#LIST} 权限。
 * 启用标签列表（/enabled）为无鉴权公共接口，供其他功能使用。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
@Validated
@Tag(name = "标签管理")
public class TagController {

    private final TagService tagService;
    private final CurrentUserApi currentUserApi;

    /**
     * 分页查询标签列表。
     *
     * @param keyword  关键词（模糊匹配标签名/编码）
     * @param status   状态过滤（ACTIVE / DISABLED）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 分页标签列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    @Operation(summary = "分页查询标签列表")
    public ResponseWrapper<CustTag> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[TagController.listPage] keyword={}, status={}, pageNo={}, pageSize={}", keyword, status, pageNo, pageSize);
        PageResult<CustTag> result = tagService.listPage(keyword, status, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询所有启用状态的标签（打标下拉选择用，无鉴权）。
     *
     * @return 启用标签列表
     */
    @GetMapping("/enabled")
    @Operation(summary = "查询启用标签列表")
    public ResponseWrapper<List<CustTag>> listEnabled() {
        log.debug("[TagController.listEnabled] called");
        return ResponseWrapper.success(tagService.listEnabled());
    }

    /**
     * 新增标签。
     *
     * @param req 创建请求 DTO
     * @return 新增标签 ID
     */
    @PostMapping
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @Operation(summary = "新增标签")
    public ResponseWrapper<String> create(@Valid @RequestBody TagCreateReqDTO req) {
        log.info("[TagController.create] tagName={}, tagCode={}", req.getTagName(), req.getTagCode());
        String empId = currentUserApi.getCurrentEmpId();
        CustTag tag = tagService.createTag(req.getTagName(), req.getTagCode(),
                req.getDescription(), req.getTagCategory(), req.getTagPriority(), empId);
        return ResponseWrapper.success(tag.getId());
    }

    /**
     * 编辑标签。
     *
     * @param id  标签 ID
     * @param req 更新请求 DTO
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @Operation(summary = "编辑标签")
    public ResponseWrapper<Void> update(@PathVariable String id,
                                        @RequestBody TagUpdateReqDTO req) {
        log.info("[TagController.update] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        tagService.updateTag(id, req.getTagName(), req.getTagCode(),
                req.getDescription(), req.getTagCategory(), req.getTagPriority(), empId);
        return ResponseWrapper.success();
    }

    /**
     * 切换标签启用/停用状态。
     *
     * @param id  标签 ID
     * @param req 状态请求 DTO
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @Operation(summary = "标签启禁用")
    public ResponseWrapper<Void> toggleStatus(@PathVariable String id,
                                              @Valid @RequestBody TagStatusReqDTO req) {
        log.info("[TagController.toggleStatus] id={}, status={}", id, req.getStatus());
        String empId = currentUserApi.getCurrentEmpId();
        tagService.toggleStatus(id, req.getStatus(), empId);
        return ResponseWrapper.success();
    }
}
