package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerOwnershipRestoreRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerProfileUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerQuery;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerTransferRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerVO;
import com.bank.branch.platform.customer.service.marketing.CustomerOwnershipService;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 页面一营销客户总列表和页面二我的客户 REST 接口。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/marketing/customers")
@Tag(name = "营销客户主档")
public class MarketingCustomerController {

    private final MarketingCustomerService customerService;
    private final CustomerOwnershipService ownershipService;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;

    /** 查询营销客户总列表；范围由当前员工的 CUSTOMER 数据权限决定。 */
    @GetMapping
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.LIST)
    @Operation(summary = "分页查询营销客户总列表")
    public ResponseWrapper<MarketingCustomerVO> list(@ModelAttribute MarketingCustomerQuery query) {
        String empId = currentUserApi.getCurrentEmpId();
        String orgId = currentUserApi.getCurrentOrgCode();
        return ResponseWrapper.page(customerService.listAll(query, empId, orgId, resolveScope(empId)));
    }

    /** 查询当前登录人主办的客户；后端固定注入当前工号过滤条件。 */
    @GetMapping("/mine")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.LIST)
    @Operation(summary = "分页查询我的营销客户")
    public ResponseWrapper<MarketingCustomerVO> mine(@ModelAttribute MarketingCustomerQuery query) {
        return ResponseWrapper.page(customerService.listMine(query, currentUserApi.getCurrentEmpId(),
                currentUserApi.getCurrentOrgCode()));
    }

    /** 查询营销客户详情，并由服务层按同一数据范围做二次校验。 */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.READ)
    @Operation(summary = "查询营销客户详情")
    public ResponseWrapper<MarketingCustomerVO> detail(@PathVariable Long id) {
        String empId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(customerService.getDetail(id, empId, currentUserApi.getCurrentOrgCode(),
                resolveScope(empId), currentUserApi.isSystemAdmin()));
    }

    /** 管理员修改客户允许维护的资料字段。 */
    @PutMapping("/{id}/profile")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE_CUSTOMER_PROFILE", resourceType = "MARKETING_CUSTOMER", reasonRequired = true)
    @Operation(summary = "修改营销客户资料")
    public ResponseWrapper<MarketingCustomerVO> updateProfile(@PathVariable Long id,
                                                               @Valid @RequestBody MarketingCustomerProfileUpdateRequest request) {
        String empId = currentUserApi.getCurrentEmpId();
        String orgId = currentUserApi.getCurrentOrgCode();
        return ResponseWrapper.success(customerService.updateProfile(id, request, empId, orgId,
                isAdmin(empId)));
    }

    /** 指定、转交或取消营销客户主办权。 */
    @PostMapping("/{id}/transfer")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.TRANSFER)
    @AuditLog(action = "TRANSFER", resourceType = "MARKETING_CUSTOMER", reasonRequired = true)
    @Operation(summary = "转交或取消营销客户主办权")
    public ResponseWrapper<Void> transfer(@PathVariable Long id,
                                          @Valid @RequestBody MarketingCustomerTransferRequest request) {
        String empId = currentUserApi.getCurrentEmpId();
        ownershipService.transfer(id, request, empId, currentUserApi.getCurrentOrgCode(), isAdmin(empId));
        return ResponseWrapper.success();
    }

    /** 恢复 AUTO 主办同步；当前无外部快照适配时由服务显式返回不可用错误。 */
    @PostMapping("/{id}/ownership/restore-auto")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.TRANSFER)
    @AuditLog(action = "RESTORE_OWNER_AUTO", resourceType = "MARKETING_CUSTOMER", reasonRequired = true)
    @Operation(summary = "恢复客户主办自动同步")
    public ResponseWrapper<Void> restoreAuto(@PathVariable Long id,
                                             @Valid @RequestBody MarketingCustomerOwnershipRestoreRequest request) {
        String empId = currentUserApi.getCurrentEmpId();
        ownershipService.restoreAuto(id, request, empId, currentUserApi.getCurrentOrgCode(), isAdmin(empId));
        return ResponseWrapper.success();
    }

    private DataScopeType resolveScope(String empId) {
        if (currentUserApi.isSystemAdmin()) {
            return DataScopeType.ALL;
        }
        DataScopeType scope = bizScopeApi.resolveScope(empId, BizType.CUSTOMER);
        return scope == null ? DataScopeType.SELF : scope;
    }

    private boolean isAdmin(String empId) {
        return currentUserApi.isSystemAdmin() || resolveScope(empId) == DataScopeType.ALL;
    }
}
