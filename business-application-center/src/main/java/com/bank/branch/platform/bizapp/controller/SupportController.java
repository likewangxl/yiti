package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.dto.req.CreateSupportReq;
import com.bank.branch.platform.bizapp.dto.resp.SupportRequestCreateRespDTO;
import com.bank.branch.platform.bizapp.service.SupportService;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中场支持申请 REST 控制器（发起侧视图）。
 * <p>
 * 提供发起侧的 8 个端点：列表、详情、创建、提交、删除、撤回、导出（501）、可用产品列表。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support-requests")
@Validated
@Tag(name = "中场支持申请管理")
public class SupportController {

    private final SupportService supportService;
    private final CurrentUserApi currentUserApi;
    private final ProductApi productApi;

    /**
     * 分页查询支持申请列表（发起侧）。
     * <p>返回 {@link SupportRequestListItemDTO}，不暴露 Entity 内部字段。</p>
     */
    @GetMapping
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.LIST)
    @Operation(summary = "查询中场支持申请列表")
    public ResponseWrapper<SupportRequestListItemDTO> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[SupportController.listPage] pageNo={}, pageSize={}", pageNo, pageSize);
        String orgCode = currentUserApi.getCurrentOrgCode();
        PageResult<SupportRequestListItemDTO> result = supportService.listPageAsDTO(keyword, status, orgCode, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询支持申请详情。
     * <p>返回 {@link SupportRequestDTO}，不暴露 Entity 内部字段（如 deleted）。</p>
     */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.READ)
    @Operation(summary = "查询中场支持申请详情")
    public ResponseWrapper<SupportRequestDTO> getById(@PathVariable String id) {
        log.info("[SupportController.getById] id={}", id);
        SupportRequestDTO result = supportService.getByIdAsDTO(id);
        return ResponseWrapper.success(result);
    }

    /**
     * 创建中场支持申请（含场景路由和自动拆单）。
     * <p>
     * 返回 {@link SupportRequestCreateRespDTO}，含 submitGroupId 供消费方做同批追溯。
     * 场景A（多产品）拆单后所有记录共享同一 submitGroupId；场景B（部门承接）单条记录独立生成。
     * </p>
     */
    @PostMapping
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST")
    @Operation(summary = "创建中场支持申请")
    public ResponseWrapper<SupportRequestCreateRespDTO> create(@Valid @RequestBody CreateSupportReq req) {
        log.info("[SupportController.create] custId={}", req.getCustId());
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        SupportRequestCreateRespDTO result = supportService.create(
                req.getProductIds(), req.getCustId(), req.getSourceTouchTaskId(),
                req.getOtherDemand(), req.getSupportDeptId(), empId, orgCode);
        return ResponseWrapper.success(result);
    }

    /**
     * 提交草稿申请进入审批。
     */
    @PostMapping("/{id}/submit")
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.WRITE)
    @AuditLog(action = "SUBMIT_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST")
    @Operation(summary = "提交中场支持申请")
    public ResponseWrapper<Void> submit(@PathVariable String id) {
        log.info("[SupportController.submit] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        supportService.submit(id, empId, orgCode);
        return ResponseWrapper.success();
    }

    /**
     * 删除草稿申请（软删除）。
     */
    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.WRITE)
    @AuditLog(action = "DELETE_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST")
    @Operation(summary = "删除草稿中场支持申请")
    public ResponseWrapper<Void> delete(@PathVariable String id) {
        log.info("[SupportController.delete] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        supportService.deleteDraft(id, empId);
        return ResponseWrapper.success();
    }

    /**
     * 撤回申请。
     */
    @PostMapping("/{id}/cancel")
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.WRITE)
    @AuditLog(action = "CANCEL_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST", reasonRequired = true)
    @Operation(summary = "撤回中场支持申请")
    public ResponseWrapper<Void> cancel(@PathVariable String id) {
        log.info("[SupportController.cancel] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        supportService.cancel(id, empId);
        return ResponseWrapper.success();
    }

    /**
     * 导出支持申请列表（预留，返回 501）。
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.EXPORT)
    @AuditLog(action = "EXPORT_SUPPORT_REQUEST", resourceType = "SUPPORT_REQUEST", reasonRequired = true)
    @Operation(summary = "导出中场支持申请（预留）")
    public ResponseWrapper<?> export() {
        log.info("[SupportController.export] 导出功能暂未实现");
        return ResponseWrapper.error("501", "Not Implemented");
    }

    /**
     * 查询支持中场支持的产品列表。
     */
    @GetMapping("/available-products")
    @BizAuth(bizType = BizType.SUPPORT, action = BizAction.READ)
    @Operation(summary = "查询可用于中场支持的产品列表")
    public ResponseWrapper<List<ProductDTO>> listAvailableProducts() {
        log.info("[SupportController.listAvailableProducts]");
        List<ProductDTO> products = productApi.listSupportAvailableProducts();
        return ResponseWrapper.success(products);
    }
}
