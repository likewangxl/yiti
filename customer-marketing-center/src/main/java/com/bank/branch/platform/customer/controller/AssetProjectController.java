package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.asset.AssetProjectCancelRequest;
import com.bank.branch.platform.customer.dto.asset.AssetProjectDeleteRequest;
import com.bank.branch.platform.customer.dto.asset.AssetProjectQuery;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSaveRequest;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSubmitResponse;
import com.bank.branch.platform.customer.dto.asset.AssetProjectUrgentContextVO;
import com.bank.branch.platform.customer.dto.asset.AssetProjectUrgentRequest;
import com.bank.branch.platform.customer.dto.asset.AssetProjectVO;
import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import com.bank.branch.platform.customer.service.AssetProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 资产立项工作台、草稿、流程与中途加急接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/marketing/asset-projects")
@Tag(name = "资产立项")
public class AssetProjectController {
    private final AssetProjectService service;
    private final CurrentUserApi currentUserApi;

    @GetMapping
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.LIST)
    @Operation(summary = "查询我的申请、待办或已办")
    public ResponseWrapper<AssetProjectVO> page(@ModelAttribute AssetProjectQuery query) {
        return ResponseWrapper.page(service.page(query, empId(), admin()));
    }

    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.READ)
    @Operation(summary = "查询资产立项统一详情")
    public ResponseWrapper<AssetProjectVO> detail(@PathVariable Long id) {
        return ResponseWrapper.success(service.detail(id, empId(), admin()));
    }

    @PostMapping
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_ASSET_PROJECT", resourceType = "ASSET_PROJECT")
    @Operation(summary = "创建资产立项草稿")
    public ResponseWrapper<AssetProjectVO> create(@Valid @RequestBody AssetProjectSaveRequest request) {
        return ResponseWrapper.success(service.create(request, empId(), orgId()));
    }

    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE_ASSET_PROJECT", resourceType = "ASSET_PROJECT")
    @Operation(summary = "修改资产立项草稿")
    public ResponseWrapper<AssetProjectVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody AssetProjectSaveRequest request) {
        return ResponseWrapper.success(service.update(id, request, empId(), admin()));
    }

    @PostMapping("/{id}/submit")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.WRITE)
    @AuditLog(action = "SUBMIT_ASSET_PROJECT", resourceType = "ASSET_PROJECT")
    @Operation(summary = "提交资产立项审批")
    public ResponseWrapper<AssetProjectSubmitResponse> submit(@PathVariable Long id) {
        return ResponseWrapper.success(service.submit(id, empId(), orgId(), admin()));
    }

    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.WRITE)
    @AuditLog(action = "DELETE_ASSET_PROJECT", resourceType = "ASSET_PROJECT", reasonRequired = true)
    @Operation(summary = "删除资产立项草稿")
    public ResponseWrapper<Void> delete(@PathVariable Long id,
                                        @Valid @ModelAttribute AssetProjectDeleteRequest request) {
        service.delete(id, request.getLockVersion(), empId(), admin());
        return ResponseWrapper.success();
    }

    @PostMapping("/{id}/cancel")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.WRITE)
    @AuditLog(action = "CANCEL_ASSET_PROJECT", resourceType = "ASSET_PROJECT", reasonRequired = true)
    @Operation(summary = "撤回资产立项审批")
    public ResponseWrapper<Void> cancel(@PathVariable Long id,
                                        @Valid @RequestBody AssetProjectCancelRequest request) {
        service.cancel(id, request.getReason(), empId(), admin());
        return ResponseWrapper.success();
    }

    @GetMapping("/{id}/urgent-context")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.READ)
    @Operation(summary = "查询中途加急上下文")
    public ResponseWrapper<AssetProjectUrgentContextVO> urgentContext(@PathVariable Long id) {
        return ResponseWrapper.success(service.urgentContext(id, empId(), admin()));
    }

    @PostMapping("/{id}/urgent-applies")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_ASSET_PROJECT_URGENT", resourceType = "ASSET_PROJECT", reasonRequired = true)
    @Operation(summary = "发起中途加急审批")
    public ResponseWrapper<AssetProjectSubmitResponse> requestUrgent(
            @PathVariable Long id, @Valid @RequestBody AssetProjectUrgentRequest request) {
        return ResponseWrapper.success(service.requestUrgent(id, request.getReason(), empId(), orgId(), admin()));
    }

    @GetMapping("/{id}/urgent-applies")
    @BizAuth(bizType = BizType.ASSET_PROJECT, action = BizAction.READ)
    @Operation(summary = "查询加急记录")
    public ResponseWrapper<List<AssetProjectUrgentApply>> urgentApplies(@PathVariable Long id) {
        return ResponseWrapper.success(service.urgentApplies(id, empId(), admin()));
    }

    private String empId() { return currentUserApi.getCurrentEmpId(); }
    private String orgId() { return currentUserApi.getCurrentOrgCode(); }
    private boolean admin() { return currentUserApi.isSystemAdmin(); }
}
