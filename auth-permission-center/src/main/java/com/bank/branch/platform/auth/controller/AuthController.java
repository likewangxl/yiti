package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.CurrentUserRespDTO;
import com.bank.branch.platform.auth.api.dto.LoginReqDTO;
import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
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
 * 负责用户登录、登出和当前用户信息查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "认证接口", description = "登录、登出和当前用户信息")
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
    public ResponseWrapper<CurrentUserRespDTO> getCurrentUser(HttpSession session) {
        log.debug("[AuthController.getCurrentUser] 获取当前用户请求");
        CurrentUserContext ctx = authService.getCurrentUser(session);
        CurrentUserRespDTO dto = new CurrentUserRespDTO();
        dto.setEmpId(ctx.empId());
        dto.setMainOrgCode(ctx.mainOrgCode());
        dto.setIsSystemAdmin(ctx.systemAdmin());
        // roles/permissions/bizScopes 由前端按需调用专属接口获取
        dto.setRoles(null);
        dto.setPermissions(null);
        dto.setBizScopes(null);
        return ResponseWrapper.success(dto);
    }
}
