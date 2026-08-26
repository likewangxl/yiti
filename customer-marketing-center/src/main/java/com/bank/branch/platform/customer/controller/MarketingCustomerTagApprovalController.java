package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.tag.TagCustomerApprovalRequest;
import com.bank.branch.platform.customer.dto.marketing.tag.TagCustomerBatchApprovalRequest;
import com.bank.branch.platform.customer.dto.marketing.tag.TagReviewRequest;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 页面六标签及标签所属客户审核接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/marketing/customer-tag-approvals")
public class MarketingCustomerTagApprovalController {

    private final MarketingCustomerTagApprovalService approvalService;
    private final CurrentUserApi currentUserApi;

    @GetMapping("/pending")
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    public ResponseWrapper<?> pending(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int pageNo,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(approvalService.pending(keyword, pageNo, pageSize));
    }

    @GetMapping("/tags/{tagId}/customers")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    public ResponseWrapper<?> pendingCustomers(@PathVariable Long tagId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(approvalService.pendingCustomers(tagId, keyword, pageNo, pageSize));
    }

    @GetMapping("/history")
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    public ResponseWrapper<?> history(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int pageNo,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(approvalService.history(currentUserApi.getCurrentEmpId(), keyword, pageNo, pageSize));
    }

    @GetMapping("/tags/{tagId}")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    public ResponseWrapper<?> tagDetail(@PathVariable Long tagId) {
        return ResponseWrapper.success(approvalService.tagDetail(tagId));
    }

    @PostMapping("/tags/{tagId}/approve")
    @BizAuth(bizType = BizType.TAG, action = BizAction.APPROVE)
    @AuditLog(action = "APPROVE_MARKETING_CUSTOMER_TAG", resourceType = "TAG")
    public ResponseWrapper<?> approveTag(@PathVariable Long tagId,
                                         @RequestBody(required = false) TagReviewRequest request) {
        return ResponseWrapper.success(approvalService.approveTag(tagId, currentUserApi.getCurrentEmpId()));
    }

    @PostMapping("/tags/{tagId}/reject")
    @BizAuth(bizType = BizType.TAG, action = BizAction.REJECT)
    @AuditLog(action = "REJECT_MARKETING_CUSTOMER_TAG", resourceType = "TAG")
    public ResponseWrapper<?> rejectTag(@PathVariable Long tagId,
                                        @Valid @RequestBody TagReviewRequest request) {
        return ResponseWrapper.success(approvalService.rejectTag(tagId, request.getOpinion(),
                currentUserApi.getCurrentEmpId()));
    }

    @PostMapping("/{detailId}/approve")
    @BizAuth(bizType = BizType.TAG, action = BizAction.APPROVE)
    @AuditLog(action = "APPROVE_MARKETING_TAG_CUSTOMER", resourceType = "TAG")
    public ResponseWrapper<?> approveOne(@PathVariable Long detailId,
                                         @RequestBody(required = false) TagCustomerApprovalRequest request) {
        TagCustomerApprovalRequest safe = request == null ? new TagCustomerApprovalRequest() : request;
        return ResponseWrapper.success(approvalService.approveOne(detailId, safe.isApproveTag(), safe.getOpinion(),
                currentUserApi.getCurrentEmpId()));
    }

    @PostMapping("/batch-approve")
    @BizAuth(bizType = BizType.TAG, action = BizAction.APPROVE)
    @AuditLog(action = "BATCH_APPROVE_MARKETING_TAG_CUSTOMERS", resourceType = "TAG")
    public ResponseWrapper<?> approveBatch(@RequestBody TagCustomerBatchApprovalRequest request) {
        return ResponseWrapper.success(approvalService.approve(request, currentUserApi.getCurrentEmpId()));
    }

    @PostMapping("/{detailId}/reject")
    @BizAuth(bizType = BizType.TAG, action = BizAction.REJECT)
    @AuditLog(action = "REJECT_MARKETING_TAG_CUSTOMER", resourceType = "TAG")
    public ResponseWrapper<?> rejectOne(@PathVariable Long detailId,
                                        @Valid @RequestBody TagCustomerApprovalRequest request) {
        return ResponseWrapper.success(approvalService.rejectOne(detailId, request.getOpinion(),
                currentUserApi.getCurrentEmpId()));
    }

    @PostMapping("/batch-reject")
    @BizAuth(bizType = BizType.TAG, action = BizAction.REJECT)
    @AuditLog(action = "BATCH_REJECT_MARKETING_TAG_CUSTOMERS", resourceType = "TAG")
    public ResponseWrapper<?> rejectBatch(@RequestBody TagCustomerBatchApprovalRequest request) {
        return ResponseWrapper.success(approvalService.reject(request, currentUserApi.getCurrentEmpId()));
    }
}
