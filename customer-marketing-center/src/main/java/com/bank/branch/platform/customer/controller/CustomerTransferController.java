package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.CustomerTransferCreateReqDTO;
import com.bank.branch.platform.customer.dto.resp.CustomerTransferRespDTO;
import com.bank.branch.platform.customer.service.CustomerTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 客户转交与转交记录接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer-transfers")
@Tag(name = "客户转交")
public class CustomerTransferController {

    private final CustomerTransferService service;
    private final CurrentUserApi currentUserApi;

    /** 发起客户主办关系转交。 */
    @PostMapping
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.TRANSFER)
    @AuditLog(action = "TRANSFER_CUSTOMER", resourceType = "CUSTOMER", reasonRequired = true)
    @Operation(summary = "发起客户转交")
    public ResponseWrapper<String> transfer(@Valid @RequestBody CustomerTransferCreateReqDTO req) {
        return ResponseWrapper.success(service.transfer(req.getCustId(), req.getTargetEmpIds(), req.getReason(),
                currentUserApi.getCurrentEmpId(), currentUserApi.getCurrentOrgCode(),
                currentUserApi.isSystemAdmin()));
    }

    /** 查询机构范围内客户转交记录。 */
    @GetMapping
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.LIST)
    @Operation(summary = "查询客户转交记录")
    public ResponseWrapper<List<CustomerTransferRespDTO>> list(@RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(service.list(keyword, currentUserApi.getCurrentOrgCode(),
                currentUserApi.isSystemAdmin()));
    }

    /** 查询启用客户经理接收人候选。 */
    @GetMapping("/candidates")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.TRANSFER)
    @Operation(summary = "查询客户转交接收人")
    public ResponseWrapper<List<UserDTO>> candidates(@RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(service.candidates(keyword));
    }
}
