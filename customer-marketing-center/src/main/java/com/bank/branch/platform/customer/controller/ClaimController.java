package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.CancelClaimReqDTO;
import com.bank.branch.platform.customer.dto.req.ClaimReqDTO;
import com.bank.branch.platform.customer.dto.req.ReTouchReqDTO;
import com.bank.branch.platform.customer.dto.req.StartTouchReqDTO;
import com.bank.branch.platform.customer.dto.resp.ClaimedCustomerRespDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户认领 REST 控制器。
 * <p>
 * 提供客户认领的创建（争抢式）、取消（高危操作）以及个人认领列表查询接口。
 * 取消认领为高危操作，配置了独立 URL、独立权限，并启用 @AuditLog 审计。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/claims")
@Validated
@Tag(name = "客户认领管理")
public class ClaimController {

    private final ClaimService claimService;
    private final CurrentUserApi currentUserApi;

    /**
     * 认领客户（争抢式）。
     * <p>
     * 若当前员工已认领该客户，返回 CUSTOMER_ALREADY_CLAIMED 业务异常。
     * </p>
     *
     * @param req 认领请求 DTO（包含 custId）
     * @return 认领记录 ID
     */
    @PostMapping
    @BizAuth(bizType = BizType.CLAIM, action = BizAction.WRITE)
    @Operation(summary = "认领客户")
    public ResponseWrapper<String> claim(@Valid @RequestBody ClaimReqDTO req) {
        log.info("[ClaimController.claim] custId={}, sourceLeadId={}", req.getCustId(), req.getSourceLeadId());
        String empId = currentUserApi.getCurrentEmpId();
        String orgId = currentUserApi.getCurrentOrgCode();
        if (req.getSourceLeadId() != null) {
            return ResponseWrapper.success(claimService.claimMarketingLead(req.getSourceLeadId(), orgId, empId));
        }
        CustClaim result = claimService.claim(req.getCustId(), orgId, empId);
        return ResponseWrapper.success(result.getId());
    }

    /**
     * 取消认领（高危操作）。
     * <p>
     * 操作需携带取消原因，由 @AuditLog 切面记录审计日志。
     * </p>
     *
     * @param id  认领记录 ID
     * @param req 取消请求 DTO（包含 reason）
     * @return 操作结果
     */
    @PostMapping("/{id}/cancel")
    @BizAuth(bizType = BizType.CLAIM, action = BizAction.WRITE)
    @AuditLog(action = "CANCEL_CLAIM", resourceType = "CLAIM", reasonRequired = true)
    @Operation(summary = "取消认领")
    public ResponseWrapper<Void> cancelClaim(@PathVariable String id,
                                             @Valid @RequestBody CancelClaimReqDTO req) {
        log.info("[ClaimController.cancelClaim] claimId={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        claimService.cancelClaim(id, req.getReason(), empId, orgCode);
        return ResponseWrapper.success();
    }

    /**
     * 对已认领客户重新发起一次触达（FOLLOW_UP）。
     * <p>
     * 业务前置校验由 {@link ClaimService#reTouch} 负责：认领必须存在（CUST-40404）、
     * 必须属于当前操作员所在机构（CUST-40305）、客户当前不存在 PENDING/IN_PROGRESS 触达任务（CUST-40908）。
     * 执行人沿用 claim.maintainerEmpId（认领时设定的维护人）。
     * </p>
     *
     * @param claimId 认领关系 ID
     * @param req     请求 DTO（reason 必填）
     * @return 新创建的 FOLLOW_UP 触达任务实体
     */
    @PostMapping("/{claimId}/re-touch")
    @BizAuth(bizType = BizType.CLAIM, action = BizAction.WRITE)
    @AuditLog(action = "RE_TOUCH_CLAIM", resourceType = "CLAIM", reasonRequired = true)
    @Operation(summary = "对已认领客户重新发起触达")
    public ResponseWrapper<TouchTask> reTouch(@PathVariable String claimId,
                                              @Valid @RequestBody ReTouchReqDTO req) {
        log.info("[ClaimController.reTouch] claimId={}", claimId);
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        TouchTask result = claimService.reTouch(claimId, req, empId, orgCode);
        return ResponseWrapper.success(result);
    }

    /** 从本人已认领客户手动发起首次触达。 */
    @PostMapping("/{claimId}/touch")
    @BizAuth(bizType = BizType.CLAIM, action = BizAction.WRITE)
    @AuditLog(action = "START_FIRST_TOUCH", resourceType = "CLAIM")
    @Operation(summary = "手动发起首次触达")
    public ResponseWrapper<TouchTask> startTouch(@PathVariable String claimId,
                                                 @RequestBody(required = false) StartTouchReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        TouchTask result = claimService.startTouch(
                claimId, req == null ? null : req.getPlanFinishTime(), empId, orgCode);
        return ResponseWrapper.success(result);
    }

    /**
     * 查询我的认领列表。
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 分页的个人认领记录列表
     */
    @GetMapping("/mine")
    @BizAuth(bizType = BizType.CLAIM, action = BizAction.LIST)
    @Operation(summary = "查询我的认领列表")
    public ResponseWrapper<CustClaim> listMyClaims(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[ClaimController.listMyClaims] pageNo={}, pageSize={}", pageNo, pageSize);
        String empId = currentUserApi.getCurrentEmpId();
        PageResult<CustClaim> result = claimService.listMyClaims(empId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /** 查询已认领客户及最近触达状态。 */
    @GetMapping("/mine/customers")
    @BizAuth(bizType = BizType.CLAIM, action = BizAction.LIST)
    @Operation(summary = "查询我的已认领客户")
    public ResponseWrapper<ClaimedCustomerRespDTO> listMyClaimedCustomers(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<ClaimedCustomerRespDTO> result = claimService.listMyClaimedCustomers(
                currentUserApi.getCurrentEmpId(), tab, keyword, sourceType, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
