package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.CrossOrgApplyCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.CrossOrgReviewReqDTO;
import com.bank.branch.platform.customer.dto.resp.CrossOrgApplyRespDTO;
import com.bank.branch.platform.customer.dto.resp.CrossOrgValidationRespDTO;
import com.bank.branch.platform.customer.entity.CrossOrgMarketingApply;
import com.bank.branch.platform.customer.service.CrossOrgMarketingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/** 跨机构客户营销申请 REST 接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cross-org-marketing")
@Tag(name = "跨机构客户营销")
public class CrossOrgMarketingController {

    private final CrossOrgMarketingService service;
    private final CurrentUserApi currentUserApi;

    /** 校验当前员工对指定客户是否满足四项申请条件。 */
    @GetMapping("/validate")
    @BizAuth(bizType = BizType.CROSS_ORG_MARKETING, action = BizAction.READ)
    @Operation(summary = "校验跨机构营销申请条件")
    public ResponseWrapper<CrossOrgValidationRespDTO> validate(
            @RequestParam(required = false) String custId,
            @RequestParam(required = false) String custNo) {
        String customerKey = StringUtils.hasText(custId) ? custId : custNo;
        if (!StringUtils.hasText(customerKey)) {
            throw new BizException("CUST-40001", "客户ID或客户号不能为空");
        }
        return ResponseWrapper.success(service.validate(customerKey, currentUserApi.getCurrentEmpId(),
                currentUserApi.getCurrentOrgCode()));
    }

    /** 提交跨机构客户营销申请。 */
    @PostMapping
    @BizAuth(bizType = BizType.CROSS_ORG_MARKETING, action = BizAction.WRITE)
    @AuditLog(action = "CREATE_CROSS_ORG_MARKETING", resourceType = "CROSS_ORG_MARKETING",
            reasonRequired = true)
    @Operation(summary = "提交跨机构营销申请")
    public ResponseWrapper<String> create(@Valid @RequestBody CrossOrgApplyCreateReqDTO req) {
        CrossOrgMarketingApply created = service.create(req.getCustId(), req.getReason(),
                currentUserApi.getCurrentEmpId(), currentUserApi.getCurrentOrgCode());
        return ResponseWrapper.success(created.getId());
    }

    /** 查询本人申请；公司部审核人员和管理员可查全量。 */
    @GetMapping
    @BizAuth(bizType = BizType.CROSS_ORG_MARKETING, action = BizAction.LIST)
    @Operation(summary = "查询跨机构营销申请")
    public ResponseWrapper<List<CrossOrgApplyRespDTO>> list(@RequestParam(required = false) String status) {
        return ResponseWrapper.success(service.list(status, currentUserApi.getCurrentEmpId(), isReviewer()));
    }

    /** 查询申请详情。 */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.CROSS_ORG_MARKETING, action = BizAction.READ)
    @Operation(summary = "查询跨机构营销申请详情")
    public ResponseWrapper<CrossOrgApplyRespDTO> get(@PathVariable String id) {
        return ResponseWrapper.success(service.get(id, currentUserApi.getCurrentEmpId(), isReviewer()));
    }

    /** 公司层面审核通过并自动生成触达任务。 */
    @PostMapping("/{id}/approve")
    @BizAuth(bizType = BizType.CROSS_ORG_MARKETING, action = BizAction.APPROVE)
    @AuditLog(action = "APPROVE_CROSS_ORG_MARKETING", resourceType = "CROSS_ORG_MARKETING",
            reasonRequired = true)
    @Operation(summary = "审核通过跨机构营销申请")
    public ResponseWrapper<Void> approve(@PathVariable String id,
                                         @Valid @RequestBody CrossOrgReviewReqDTO req) {
        service.approve(id, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    /** 公司层面退回跨机构营销申请。 */
    @PostMapping("/{id}/reject")
    @BizAuth(bizType = BizType.CROSS_ORG_MARKETING, action = BizAction.REJECT)
    @AuditLog(action = "REJECT_CROSS_ORG_MARKETING", resourceType = "CROSS_ORG_MARKETING",
            reasonRequired = true)
    @Operation(summary = "退回跨机构营销申请")
    public ResponseWrapper<Void> reject(@PathVariable String id,
                                        @Valid @RequestBody CrossOrgReviewReqDTO req) {
        service.reject(id, req.getReason(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }

    private boolean isReviewer() {
        if (currentUserApi.isSystemAdmin()) return true;
        Set<String> roles = currentUserApi.getCurrentRoleCodes();
        return roles != null && roles.stream().anyMatch(role -> Set.of(
                "CORP_DEPT", "CORP_DEPT_LEADER", "RETAIL_DEPT", "RETAIL_DEPT_LEADER").contains(role));
    }
}
