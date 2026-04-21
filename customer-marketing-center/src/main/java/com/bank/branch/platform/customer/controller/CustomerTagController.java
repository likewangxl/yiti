package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.CustomerTagAddReqDTO;
import com.bank.branch.platform.customer.service.TagCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户打标管理 REST 控制器。
 * <p>
 * 提供面向客户视角的标签管理端点：追加打标（幂等）和取消单个标签。
 * 与 {@link TagCustomerController} 的区别在于操作入口：
 * 本控制器以客户 ID 为主键，从客户侧管理其标签集合；
 * TagCustomerController 以标签 ID 为主键，用于覆盖式批量导入。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Validated
@Tag(name = "客户打标管理")
public class CustomerTagController {

    private final TagCustomerService tagCustomerService;
    private final CurrentUserApi currentUserApi;

    /**
     * 给客户追加标签（幂等，不覆盖已有标签）。
     * <p>
     * 已存在的标签关联会被跳过，只新增不存在的关联。
     * 返回本次实际新增的标签数量。
     * </p>
     *
     * @param id  客户 ID
     * @param req 包含待追加标签 ID 列表的请求体
     * @return 实际新增的标签关联数量
     */
    @PostMapping("/{id}/tags")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.WRITE)
    @AuditLog(action = "ADD_CUSTOMER_TAGS", resourceType = "CUSTOMER")
    @Operation(summary = "客户追加打标（幂等，已存在的标签跳过）")
    public ResponseWrapper<Integer> addTags(@PathVariable String id,
                                             @Valid @RequestBody CustomerTagAddReqDTO req) {
        log.info("[CustomerTagController.addTags] custId={}, tagCount={}", id, req.getTagIds().size());
        String empId = currentUserApi.getCurrentEmpId();
        int added = tagCustomerService.addTagsToCustomer(id, req.getTagIds(), empId);
        return ResponseWrapper.success(added);
    }

    /**
     * 取消客户某个标签（物理删除关联记录）。
     * <p>
     * 关联不存在时返回 false，存在并删除成功时返回 true，为幂等操作。
     * </p>
     *
     * @param id    客户 ID
     * @param tagId 待取消的标签 ID
     * @return true 表示删除成功，false 表示关联不存在
     */
    @DeleteMapping("/{id}/tags/{tagId}")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.WRITE)
    @AuditLog(action = "REMOVE_CUSTOMER_TAG", resourceType = "CUSTOMER")
    @Operation(summary = "取消客户某个标签")
    public ResponseWrapper<Boolean> removeTag(@PathVariable String id,
                                               @PathVariable String tagId) {
        log.info("[CustomerTagController.removeTag] custId={}, tagId={}", id, tagId);
        boolean removed = tagCustomerService.removeTagFromCustomer(id, tagId);
        return ResponseWrapper.success(removed);
    }
}
