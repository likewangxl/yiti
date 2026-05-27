package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleBindReqDTO;
import com.bank.branch.platform.auth.service.UserRoleService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 用户角色绑定控制器
 * 提供用户角色的查询、绑定和解绑接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users/{userId}/roles")
@Tag(name = "用户角色管理", description = "用户角色绑定/解绑操作")
public class UserRoleController {

    private final UserRoleService userRoleService;

    /**
     * 查询用户已绑定的角色列表
     *
     * @param userId 用户ID（路径参数）
     * @return 角色简要信息列表
     */
    @GetMapping("")
    @Operation(summary = "查询用户已绑定角色列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<RoleSimpleDTO>> getUserRoles(@PathVariable("userId") String userId) {
        log.debug("[UserRoleController.getUserRoles] userId={}", userId);
        List<RoleSimpleDTO> roles = userRoleService.getRolesByUserId(userId);
        return ResponseWrapper.success(roles);
    }

    /**
     * 批量绑定角色到用户
     *
     * @param userId 用户ID（路径参数）
     * @param req    绑定请求DTO（含角色ID列表和操作原因）
     * @return 成功响应
     */
    @PostMapping("")
    @Operation(summary = "批量绑定角色到用户")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    @com.bank.branch.platform.common.aop.annotation.AuditLog(action = "USER_ROLE_BIND", resourceType = "USER_ROLE", reasonRequired = true)
    public ResponseWrapper<Void> bindRoles(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UserRoleBindReqDTO req) {
        log.info("[UserRoleController.bindRoles] userId={}, roleIds={}", userId, req.getRoleIds());
        userRoleService.bindRoles(userId, req.getRoleIds(), req.getReason());
        return ResponseWrapper.success();
    }

    /**
     * 解绑用户的指定角色
     *
     * @param userId 用户ID（路径参数）
     * @param roleId 角色ID（路径参数）
     * @param reason 解绑原因（审计用）
     * @return 成功响应
     */
    @DeleteMapping("/{roleId}")
    @Operation(summary = "解绑用户角色")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> unbindRole(
            @PathVariable("userId") String userId,
            @PathVariable("roleId") String roleId,
            @RequestParam("reason") String reason) {
        log.info("[UserRoleController.unbindRole] userId={}, roleId={}, reason={}", userId, roleId, reason);
        userRoleService.unbindRole(userId, roleId, reason);
        return ResponseWrapper.success();
    }
}
