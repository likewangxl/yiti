package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.tag.TagCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.tag.TagUpdateRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 页面五营销客户标签及正式客户群接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/marketing/customer-tags")
@Tag(name = "营销客户标签")
public class MarketingCustomerTagController {

    private final MarketingCustomerTagService tagService;
    private final CurrentUserApi currentUserApi;

    @GetMapping
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    @Operation(summary = "查询营销客户标签")
    public ResponseWrapper<?> list(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String tagType,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String approvalStatus,
                                   @RequestParam(required = false) String viewStatus,
                                   @RequestParam(defaultValue = "1") int pageNo,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(tagService.list(keyword, category, tagType, status,
                approvalStatus, viewStatus, pageNo, pageSize));
    }

    @PostMapping
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_MARKETING_CUSTOMER_TAG", resourceType = "TAG")
    @Operation(summary = "新增标签并进入审批")
    public ResponseWrapper<MarketingCustomerTag> create(@Valid @RequestBody TagCreateRequest request) {
        return ResponseWrapper.success(tagService.create(request, currentUserApi.getCurrentEmpId(),
                currentUserApi.getCurrentOrgCode()));
    }

    @GetMapping("/{tagId}")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    public ResponseWrapper<MarketingCustomerTag> detail(@PathVariable Long tagId) {
        return ResponseWrapper.success(tagService.get(tagId));
    }

    @PutMapping("/{tagId}")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE_MARKETING_CUSTOMER_TAG", resourceType = "TAG")
    public ResponseWrapper<MarketingCustomerTag> update(@PathVariable Long tagId,
                                                         @Valid @RequestBody TagUpdateRequest request) {
        return ResponseWrapper.success(tagService.update(tagId, request, currentUserApi.getCurrentEmpId()));
    }

    @GetMapping("/{tagId}/customers")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    public ResponseWrapper<?> customers(@PathVariable Long tagId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") int pageNo,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(tagService.listCustomers(tagId, keyword, pageNo, pageSize));
    }
}
