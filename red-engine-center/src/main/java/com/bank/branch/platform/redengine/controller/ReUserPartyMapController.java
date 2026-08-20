package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.common.web.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseWrapper<PageResult<ReUserPartyMapDTO>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "partyOrgId", required = false) Long partyOrgId,
            @RequestParam(value = "partyRole", required = false) String partyRole) {
        PageResult<ReUserPartyMapDTO> result = reUserPartyMapService.page(
                pageNo, pageSize, username, displayName, partyOrgId, partyRole);
        log.info("[ReUserPartyMapController.list] pageNo={}, pageSize={}, total={}",
                result.getPageNo(), result.getPageSize(), result.getTotal());
        return ResponseWrapper.success(result);
    }

    @Operation(summary = "绑定用户党组织")
    @PostMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.CONFIG)
    // 种子表 P_RE_MAP_BIND 注释标注为"高危"，但经 Task 6 评审裁决维持 reasonRequired=false（简报权威口径）：
    // 不强制填写审计原因，@AuditLog 切面仍会记录操作人与时间戳，留痕问责不依赖 reason 字段。
    // 若后续评审要求升级为强制填 reason，需联动改 ReUserPartyMapDTO 增加 reason 字段并另开任务。
    @AuditLog(action = "RE_MAP_BIND", resourceType = "RE_USER_PARTY_MAP", reasonRequired = false)
    public ResponseWrapper<Void> bind(@Valid @RequestBody ReUserPartyMapDTO req) {
        log.info("[ReUserPartyMapController.bind] userId={}, partyOrgId={}, partyRole={}",
                req.getUserId(), req.getPartyOrgId(), req.getPartyRole());
        reUserPartyMapService.bind(req.getUserId(), req.getPartyOrgId(), req.getPartyRole());
        return ResponseWrapper.success();
    }
}
