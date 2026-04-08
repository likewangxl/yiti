package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.CheckPermissionReqDTO;
import com.bank.branch.platform.auth.api.dto.CheckPermissionRespDTO;
import com.bank.branch.platform.auth.api.dto.CurrentUserRespDTO;
import com.bank.branch.platform.auth.api.dto.LoginReqDTO;
import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.api.dto.PermissionSetRespDTO;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 负责用户登录、登出、当前用户信息查询、权限查询与校验接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "认证接口", description = "登录、登出、当前用户信息查询、权限查询与校验")
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     *
     * @param req     登录请求DTO（包含用户名和密码）
     * @param session HttpSession，用于存储登录态
     * @return 登录响应（含 token、角色等信息）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验用户名和密码，登录成功后返回 Session Token")
    public ResponseWrapper<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO req, HttpSession session) {
        log.info("[AuthController.login] 用户登录请求 username={}", req.getUsername());
        LoginRespDTO resp = authService.login(req.getUsername(), req.getPassword(), session);
        return ResponseWrapper.success(resp);
    }

    /**
     * 用户登出
     *
     * @param session HttpSession，登出时销毁
     * @return 成功响应
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "销毁当前 Session，清除登录状态")
    public ResponseWrapper<Void> logout(HttpSession session) {
        log.info("[AuthController.logout] 用户登出请求");
        authService.logout(session);
        return ResponseWrapper.success();
    }

    /**
     * 获取当前登录用户信息
     *
     * @param session HttpSession，从中读取用户上下文
     * @return 当前用户详情DTO
     */
    @GetMapping("/current-user")
    @Operation(summary = "获取当前用户信息", description = "返回当前登录用户的权限和身份信息")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<CurrentUserRespDTO> getCurrentUser(HttpSession session) {
        log.debug("[AuthController.getCurrentUser] 获取当前用户请求");
        CurrentUserContext ctx = authService.getCurrentUser(session);
        CurrentUserRespDTO dto = new CurrentUserRespDTO();
        dto.setEmpId(ctx.empId());
        dto.setUsername(ctx.username());
        dto.setDisplayName(ctx.displayName());
        dto.setMainOrgCode(ctx.mainOrgCode());
        dto.setMainOrgName(ctx.mainOrgName());
        dto.setOrgLevel(ctx.orgLevel());
        dto.setIsSystemAdmin(ctx.systemAdmin());
        // roles/permissions/bizScopes 由前端按需调用专属接口获取
        dto.setRoles(null);
        dto.setPermissions(null);
        dto.setBizScopes(null);
        return ResponseWrapper.success(dto);
    }

    /**
     * 获取当前用户完整权限集合（H.1）
     *
     * @param session HttpSession，从中读取用户上下文
     * @return 当前用户的资源URL集合、BizScope映射、角色列表
     */
    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户完整权限集合",
            description = "返回当前用户所有有权限的资源URL列表和BizScope映射，供前端/网关做完整权限判断")
    public ResponseWrapper<PermissionSetRespDTO> getPermissions(HttpSession session) {
        log.debug("[AuthController.getPermissions] 获取当前用户权限集合请求");
        CurrentUserContext ctx = authService.getCurrentUser(session);
        PermissionSetRespDTO dto = authService.getUserPermissions(ctx.empId());
        return ResponseWrapper.success(dto);
    }

    /**
     * 校验当前用户是否有指定资源/BizType的权限（H.2）
     *
     * @param req 权限检查请求（resourceUrl、resourceMethod必填，bizType/action可选）
     * @param session HttpSession，从中读取用户上下文
     * @return 权限检查结果（含RBAC和数据范围检查详情）
     */
    @PostMapping("/check-permission")
    @Operation(summary = "校验权限",
            description = "校验当前用户是否有指定资源/BizType的权限，返回详细检查结论")
    public ResponseWrapper<CheckPermissionRespDTO> checkPermission(
            @Valid @RequestBody CheckPermissionReqDTO req, HttpSession session) {
        log.debug("[AuthController.checkPermission] 权限校验请求 url={}, method={}",
                req.getResourceUrl(), req.getResourceMethod());
        authService.getCurrentUser(session); // 确保已登录
        CheckPermissionRespDTO dto = authService.checkPermission(req);
        return ResponseWrapper.success(dto);
    }
}
