package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReUserPartyMapDTO;
import com.bank.branch.platform.redengine.service.ReUserPartyMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 红色引擎-用户党组织映射管理端点。
 * <p>URL 与 Task 4 权限种子对照表严格一致：GET/POST 均为 {@code /api/re/user-party-maps}，
 * 分别对应 PT_RESOURCE 资源号 P_RE_MAP_LIST / P_RE_MAP_BIND（仅 R_ADMIN 角色授权）。</p>
 */
@Slf4j
@Tag(name = "红色引擎-用户党组织映射")
@RestController
@RequestMapping("/api/re/user-party-maps")
@RequiredArgsConstructor
public class ReUserPartyMapController {

    private final ReUserPartyMapService reUserPartyMapService;

    @Operation(summary = "用户党组织映射列表")
    @GetMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<List<ReUserPartyMapDTO>> list() {
        List<ReUserPartyMapDTO> result = reUserPartyMapService.list();
        log.info("[ReUserPartyMapController.list] size={}", result.size());
        return ResponseWrapper.success(result);
    }

    @Operation(summary = "绑定用户党组织")
    @PostMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.CONFIG)
    @AuditLog(action = "RE_MAP_BIND", resourceType = "RE_USER_PARTY_MAP", reasonRequired = false)
    public ResponseWrapper<Void> bind(@Valid @RequestBody ReUserPartyMapDTO req) {
        log.info("[ReUserPartyMapController.bind] userId={}, partyOrgId={}, partyRole={}",
                req.getUserId(), req.getPartyOrgId(), req.getPartyRole());
        reUserPartyMapService.bind(req.getUserId(), req.getPartyOrgId(), req.getPartyRole());
        return ResponseWrapper.success();
    }
}
