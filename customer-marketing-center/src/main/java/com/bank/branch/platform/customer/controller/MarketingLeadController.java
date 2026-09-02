package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadQuery;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadSubmitResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.MarketingCustomerSnapshot;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 页面三线索录入记录与手工草稿接口。 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/marketing/leads")
@Tag(name = "营销线索录入")
public class MarketingLeadController {

    private final MarketingLeadEntryService service;
    private final CurrentUserApi currentUserApi;

    @GetMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "查询本人手工录入和批量导入线索记录")
    public ResponseWrapper<MarketingLeadInfo> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        LeadQuery query = new LeadQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        PageResult<MarketingLeadInfo> result = service.list(query, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.page(result);
    }

    @GetMapping("/lookup")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "按客户名称或统一社会信用代码反显客户主档")
    public ResponseWrapper<MarketingCustomerSnapshot> lookup(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String unifiedCreditCode) {
        return ResponseWrapper.success(service.lookupCustomer(customerName, unifiedCreditCode));
    }

    @GetMapping("/{leadId}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询本人线索录入详情")
    public ResponseWrapper<LeadDetailResponse> detail(@PathVariable Long leadId) {
        return ResponseWrapper.success(service.getDetail(leadId, currentUserApi.getCurrentEmpId()));
    }

    @PostMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_MARKETING_LEAD", resourceType = "LEAD")
    @Operation(summary = "创建手工线索草稿")
    public ResponseWrapper<MarketingLeadInfo> create(@Valid @RequestBody LeadCreateRequest request) {
        return ResponseWrapper.success(service.createDraft(request,
                currentUserApi.getCurrentEmpId(), currentUserApi.getCurrentOrgCode()));
    }

    @PutMapping("/{leadId}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE_MARKETING_LEAD", resourceType = "LEAD")
    @Operation(summary = "修改手工线索草稿")
    public ResponseWrapper<MarketingLeadInfo> update(@PathVariable Long leadId,
                                                     @Valid @RequestBody LeadUpdateRequest request) {
        return ResponseWrapper.success(service.updateDraft(leadId, request,
                currentUserApi.getCurrentEmpId(), currentUserApi.getCurrentOrgCode()));
    }

    @PostMapping("/{leadId}/submit")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @AuditLog(action = "SUBMIT_MARKETING_LEAD", resourceType = "LEAD")
    @Operation(summary = "提交手工线索审批")
    public ResponseWrapper<LeadSubmitResponse> submit(@PathVariable Long leadId) {
        return ResponseWrapper.success(service.submit(leadId,
                currentUserApi.getCurrentEmpId(), currentUserApi.getCurrentOrgCode()));
    }

    @PostMapping("/{leadId}/cancel")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @AuditLog(action = "CANCEL_MARKETING_LEAD", resourceType = "LEAD", reasonRequired = true)
    @Operation(summary = "取消手工线索")
    public ResponseWrapper<Void> cancel(@PathVariable Long leadId) {
        service.cancel(leadId, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /** 删除草稿保留为取消语义，避免物理删除导入/审批审计链。 */
    @DeleteMapping("/{leadId}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.DELETE)
    @AuditLog(action = "CANCEL_MARKETING_LEAD", resourceType = "LEAD", reasonRequired = true)
    @Operation(summary = "取消草稿线索")
    public ResponseWrapper<Void> delete(@PathVariable Long leadId) {
        service.cancel(leadId, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }
}
