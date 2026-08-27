package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadApprovalDecisionRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadApprovalTaskResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 页面四线索审批接口；工作流任务查询/办理通过 workflow-center 公开 API。 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/marketing/lead-approvals")
@Tag(name = "营销线索审批")
public class MarketingLeadApprovalController {

    private final MarketingLeadApprovalService service;
    private final CurrentUserApi currentUserApi;

    @GetMapping("/pending")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "查询待审批线索")
    public ResponseWrapper<LeadApprovalTaskResponse> pending(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(service.pending(keyword, pageNo, pageSize,
                currentUserApi.getCurrentEmpId()));
    }

    @GetMapping("/history")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "查询本人审批记录")
    public ResponseWrapper<LeadApprovalTaskResponse> history(
            @RequestParam(required = false) String keyword,
            @RequestParam String result,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseWrapper.page(service.history(keyword, result, pageNo, pageSize,
                currentUserApi.getCurrentEmpId()));
    }

    @GetMapping("/{leadId}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询线索审批详情")
    public ResponseWrapper<LeadDetailResponse> detail(@PathVariable Long leadId) {
        return ResponseWrapper.success(service.detail(leadId, currentUserApi.getCurrentEmpId()));
    }

    @PostMapping("/{leadId}/approve")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.APPROVE)
    @AuditLog(action = "APPROVE_MARKETING_LEAD", resourceType = "LEAD")
    @Operation(summary = "审批通过线索")
    public ResponseWrapper<Void> approve(@PathVariable Long leadId,
                                         @Valid @RequestBody LeadApprovalDecisionRequest request) {
        service.approve(leadId, request.getTaskId(), currentUserApi.getCurrentEmpId(), request.getOpinion());
        return ResponseWrapper.success();
    }

    @PostMapping("/{leadId}/reject")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.REJECT)
    @AuditLog(action = "REJECT_MARKETING_LEAD", resourceType = "LEAD")
    @Operation(summary = "驳回线索")
    public ResponseWrapper<Void> reject(@PathVariable Long leadId,
                                        @Valid @RequestBody LeadApprovalDecisionRequest request) {
        service.reject(leadId, request.getTaskId(), currentUserApi.getCurrentEmpId(), request.getOpinion());
        return ResponseWrapper.success();
    }
}
