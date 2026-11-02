package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.*;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.*;
import com.bank.branch.platform.governance.service.LoginScreenRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 登录首屏规则管理及当前用户读取接口。 */
@RestController
@RequiredArgsConstructor
public class LoginScreenRuleController {
    private final LoginScreenRuleService service;
    @GetMapping("/api/admin/sys/login-screens/{roleId}") @BizAuth(bizType=BizType.SYS_CONFIG, action=BizAction.READ)
    public ResponseWrapper<LoginScreenRuleDTO> get(@PathVariable("roleId") String roleId) { return ResponseWrapper.success(service.getForRole(roleId)); }
    @PutMapping("/api/admin/sys/login-screens/{roleId}") @BizAuth(bizType=BizType.SYS_CONFIG, action=BizAction.CONFIG)
    public ResponseWrapper<LoginScreenRuleDTO> save(@PathVariable("roleId") String roleId, @Valid @RequestBody LoginScreenRuleSaveReqDTO req) { return ResponseWrapper.success(service.save(roleId, req)); }
    @GetMapping("/api/sys/login-screens") @BizAuth(bizType=BizType.SYS_CONFIG, action=BizAction.READ)
    public ResponseWrapper<LoginScreenRulesRespDTO> current() { return ResponseWrapper.success(service.current()); }
}
