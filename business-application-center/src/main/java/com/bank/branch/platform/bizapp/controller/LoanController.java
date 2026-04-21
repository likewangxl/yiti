package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.bizapp.api.dto.LoanApplyListItemDTO;
import com.bank.branch.platform.bizapp.dto.req.CreateLoanReq;
import com.bank.branch.platform.bizapp.dto.req.UpdateLoanReq;
import com.bank.branch.platform.bizapp.dto.resp.LoanDetailResp;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.service.LoanFormValidator;
import com.bank.branch.platform.bizapp.service.LoanService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产投放申请 REST 控制器。
 * <p>
 * 提供贷款申请的 CRUD、提交审批、撤回及节点表单查询接口。
 * 所有写操作需要 {@link BizAction#WRITE} 权限，查询需要 {@link BizAction#LIST} 权限。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/loans")
@Validated
@Tag(name = "资产投放申请管理")
public class LoanController {

    private final LoanService loanService;
    private final LoanFormValidator loanFormValidator;
    private final CurrentUserApi currentUserApi;

    /**
     * 分页查询贷款申请列表。
     *
     * @param keyword    关键词（模糊匹配申请编号/客户ID）
     * @param status     状态过滤
     * @param ownerOrgId 归属机构过滤
     * @param pageNo     页码，默认 1
     * @param pageSize   每页大小，默认 20
     * @return 分页贷款申请列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.LOAN, action = BizAction.LIST)
    @Operation(summary = "分页查询贷款申请列表")
    public ResponseWrapper<LoanApplyListItemDTO> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownerOrgId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[LoanController.listPage] keyword={}, status={}, ownerOrgId={}, pageNo={}, pageSize={}",
                keyword, status, ownerOrgId, pageNo, pageSize);
        PageResult<LoanApplyListItemDTO> result = loanService.listPageAsDTO(keyword, status, ownerOrgId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询贷款申请详情。
     *
     * @param id 申请ID
     * @return 申请详情
     */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.READ)
    @Operation(summary = "查询贷款申请详情")
    public ResponseWrapper<LoanDetailResp> getById(@PathVariable String id) {
        log.info("[LoanController.getById] id={}", id);
        LoanApply entity = loanService.getById(id);
        return ResponseWrapper.success(LoanDetailResp.from(entity));
    }

    /**
     * 创建贷款申请草稿。
     *
     * @param req 创建请求
     * @return 创建后的申请详情
     */
    @PostMapping
    @BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
    @Operation(summary = "创建贷款申请草稿")
    public ResponseWrapper<LoanDetailResp> create(@RequestBody @Valid CreateLoanReq req) {
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        log.info("[LoanController.create] custId={}, operator={}", req.getCustId(), empId);
        LoanApply entity = loanService.createDraft(
                req.getCustId(), req.getSourceTouchTaskId(),
                req.getProjectType(), req.getBizType(), req.getGuaranteeType(),
                req.getCreditAmount(), req.getCreditExposureAmount(),
                empId, orgCode
        );
        return ResponseWrapper.success(LoanDetailResp.from(entity));
    }

    /**
     * 更新贷款申请草稿。
     *
     * @param id  申请ID
     * @param req 更新请求
     * @return 更新后的申请详情
     */
    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
    @Operation(summary = "更新贷款申请草稿")
    public ResponseWrapper<LoanDetailResp> update(@PathVariable String id,
                                                   @RequestBody @Valid UpdateLoanReq req) {
        String empId = currentUserApi.getCurrentEmpId();
        log.info("[LoanController.update] id={}, operator={}", id, empId);
        LoanApply entity = loanService.updateDraft(
                id, req.getProjectType(), req.getBizType(), req.getGuaranteeType(),
                req.getCreditAmount(), req.getCreditExposureAmount(), empId
        );
        return ResponseWrapper.success(LoanDetailResp.from(entity));
    }

    /**
     * 提交贷款申请审批。
     *
     * @param id 申请ID
     * @return 成功响应
     */
    @PostMapping("/{id}/submit")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
    @Operation(summary = "提交贷款申请审批")
    public ResponseWrapper<Void> submit(@PathVariable String id) {
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        log.info("[LoanController.submit] id={}, operator={}", id, empId);
        loanService.submitForApproval(id, empId, orgCode);
        return ResponseWrapper.success();
    }

    /**
     * 逻辑删除贷款申请草稿。
     *
     * @param id 申请ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
    @Operation(summary = "删除贷款申请草稿")
    public ResponseWrapper<Void> delete(@PathVariable String id) {
        String empId = currentUserApi.getCurrentEmpId();
        log.info("[LoanController.delete] id={}, operator={}", id, empId);
        loanService.deleteDraft(id, empId);
        return ResponseWrapper.success();
    }

    /**
     * 撤回贷款申请。
     *
     * @param id 申请ID
     * @return 成功响应
     */
    @PostMapping("/{id}/cancel")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
    @Operation(summary = "撤回贷款申请")
    public ResponseWrapper<Void> cancel(@PathVariable String id) {
        String empId = currentUserApi.getCurrentEmpId();
        log.info("[LoanController.cancel] id={}, operator={}", id, empId);
        loanService.cancelApply(id, empId);
        return ResponseWrapper.success();
    }

    /**
     * 导出贷款申请列表（V1 未实现，返回 501）。
     *
     * @return 501 未实现响应
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.EXPORT)
    @Operation(summary = "导出贷款申请列表（待实现）")
    public ResponseWrapper<?> export() {
        log.info("[LoanController.export] V1 暂未实现导出功能");
        return ResponseWrapper.error("501", "Not Implemented");
    }

    /**
     * 查询节点表单配置。
     * <p>
     * V1 委托 LoanFormValidator 返回节点表单校验规则，或返回空。
     * </p>
     *
     * @param id      申请ID
     * @param nodeKey 节点标识
     * @return 节点表单信息
     */
    @GetMapping("/{id}/node-form/{nodeKey}")
    @BizAuth(bizType = BizType.LOAN, action = BizAction.READ)
    @Operation(summary = "查询节点表单配置")
    public ResponseWrapper<Object> getNodeForm(@PathVariable String id,
                                                @PathVariable String nodeKey) {
        log.info("[LoanController.getNodeForm] id={}, nodeKey={}", id, nodeKey);
        // V1：返回空对象，表示节点表单配置由前端自行处理
        return ResponseWrapper.success(null);
    }
}
