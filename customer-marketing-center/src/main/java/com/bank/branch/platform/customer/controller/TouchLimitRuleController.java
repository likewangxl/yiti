package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.TouchLimitRuleUpdateReqDTO;
import com.bank.branch.platform.customer.dto.resp.TouchLimitRuleRespDTO;
import com.bank.branch.platform.customer.service.TouchLimitRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 客户标签触达周期规则 REST 控制器。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/touch-limit-rules")
@Validated
@Tag(name = "客户触达周期管理")
public class TouchLimitRuleController {

    private final TouchLimitRuleService service;
    private final CurrentUserApi currentUserApi;

    /** 分页查询所有未删除客户标签及其触达周期规则。 */
    @GetMapping
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    @Operation(summary = "分页查询客户标签触达周期规则")
    public ResponseWrapper<TouchLimitRuleRespDTO> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<TouchLimitRuleRespDTO> result = service.listPage(keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /** 修改客户标签触达周期规则。 */
    @PutMapping("/{tagId}")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    @AuditLog(action = "UPDATE_TOUCH_LIMIT_RULE", resourceType = "TAG")
    @Operation(summary = "修改客户标签触达周期规则")
    public ResponseWrapper<Void> update(@PathVariable String tagId,
                                        @Valid @RequestBody TouchLimitRuleUpdateReqDTO request) {
        String operatorEmpId = currentUserApi.getCurrentEmpId();
        service.updateRule(tagId, request, operatorEmpId);
        return ResponseWrapper.success();
    }
}
