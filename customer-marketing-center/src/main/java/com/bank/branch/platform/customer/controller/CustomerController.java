package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.DeleteApplyReqDTO;
import com.bank.branch.platform.customer.dto.req.TransferReqDTO;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.service.CustomerService;
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
 * 客户主档 REST 控制器。
 * <p>
 * 提供客户主档的分页查询、详情查询、维护人转交、删除申请接口。
 * 所有写操作需要对应 BizAction 权限，查询操作需要 LIST/READ 权限。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Validated
@Tag(name = "客户主档管理")
public class CustomerController {

    private final CustomerService customerService;
    private final CurrentUserApi currentUserApi;

    /**
     * 分页查询客户主档列表。
     *
     * @param keyword  关键词（模糊匹配客户名/统一信用代码）
     * @param status   客户状态过滤（ACTIVE / INACTIVE）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 分页客户主档列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.LIST)
    @Operation(summary = "分页查询客户主档列表")
    public ResponseWrapper<CustomerDTO> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[CustomerController.listPage] keyword={}, status={}, pageNo={}, pageSize={}",
                keyword, status, pageNo, pageSize);
        PageResult<CustomerDTO> result = customerService.listPageAsDTO(keyword, status, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询客户主档详情。
     *
     * @param id 客户主档 ID
     * @return 客户主档详情
     */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.READ)
    @Operation(summary = "查询客户主档详情")
    public ResponseWrapper<CustomerDTO> getById(@PathVariable String id) {
        log.info("[CustomerController.getById] id={}", id);
        CustomerDTO dto = customerService.getByIdAsDTO(id);
        return ResponseWrapper.success(dto);
    }

    /**
     * 转交客户维护人（高危操作，必须提供原因）。
     *
     * @param custId   客户主档 ID（路径参数，用于权限范围校验）
     * @param claimId  认领记录 ID
     * @param req      转交请求（toEmpId + reason）
     * @return 操作结果
     */
    @PostMapping("/{custId}/claims/{claimId}/transfer")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.WRITE)
    @AuditLog(action = "TRANSFER", resourceType = "CUSTOMER", reasonRequired = true)
    @Operation(summary = "转交客户维护人")
    public ResponseWrapper<Void> transfer(@PathVariable String custId,
                                          @PathVariable String claimId,
                                          @Valid @RequestBody TransferReqDTO req) {
        log.info("[CustomerController.transfer] custId={}, claimId={}, toEmpId={}",
                custId, claimId, req.getToEmpId());
        String operatorEmpId = currentUserApi.getCurrentEmpId();
        customerService.transfer(claimId, req.getToEmpId(), req.getReason(), operatorEmpId);
        return ResponseWrapper.success();
    }

    /**
     * 发起客户删除申请（提交审批流）。
     *
     * @param custId 客户主档 ID
     * @param req    删除申请请求（reason 可为空）
     * @return 操作结果
     */
    @PostMapping("/{custId}/delete-apply")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.DELETE)
    @Operation(summary = "发起客户删除申请")
    public ResponseWrapper<Void> deleteApply(@PathVariable String custId,
                                             @RequestBody(required = false) DeleteApplyReqDTO req) {
        log.info("[CustomerController.deleteApply] custId={}", custId);
        String operatorEmpId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        customerService.deleteApply(custId, operatorEmpId, orgCode);
        return ResponseWrapper.success();
    }
}
