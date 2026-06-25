package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO;
import com.bank.branch.platform.auth.api.dto.UserCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.api.dto.UserListItemRespDTO;
import com.bank.branch.platform.auth.api.dto.UserQueryReqDTO;
import com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO;
import com.bank.branch.platform.auth.service.UserService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.controller.dto.UserExportRow;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 用户管理 REST 控制器
 * 提供 CRUD、批量启用/禁用/锁定/解锁、密码重置/修改 12 个接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Tag(name = "用户管理", description = "用户 CRUD / 状态变更 / 密码")
public class UserController {

    private final UserService userService;
    private final CurrentUserApi currentUserApi;

    // ------------------------------------------------------------------ 查询

    @GetMapping("")
    @Operation(summary = "分页查询用户列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PageResult<UserListItemRespDTO>> list(@ModelAttribute UserQueryReqDTO q) {
        log.debug("[UserController.list] q={}", q);
        return ResponseWrapper.success(userService.pageUsers(q));
    }

    @GetMapping("/export")
    @Operation(summary = "导出全部用户（含绑定角色）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public void export(HttpServletResponse response) throws java.io.IOException {
        log.info("[UserController.export] 导出全部用户");
        java.util.List<UserExportRow> rows = userService.exportAllUsers();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("用户列表", java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), UserExportRow.class)
                .sheet("用户列表")
                .doWrite(rows);
    }

    @GetMapping("/{username}/exists")
    @Operation(summary = "用户名是否已存在")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<Boolean> exists(@PathVariable("username") String username) {
        return ResponseWrapper.success(userService.existsByUsername(username));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "加载用户详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<UserDetailRespDTO> getById(@PathVariable("userId") String userId) {
        return ResponseWrapper.success(userService.getById(userId));
    }

    // ------------------------------------------------------------------ CRUD

    @PostMapping("")
    @Operation(summary = "新增用户")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> create(@Valid @RequestBody UserCreateReqDTO req) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.create] userId={} operator={}", req.getUserId(), operator);
        userService.create(req, operator);
        return ResponseWrapper.success();
    }

    @PutMapping("/{userId}")
    @Operation(summary = "修改用户")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> update(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UserUpdateReqDTO req) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.update] userId={} operator={}", userId, operator);
        userService.update(userId, req, operator);
        return ResponseWrapper.success();
    }

    @DeleteMapping("/{ids}")
    @Operation(summary = "批量删除用户（ids 逗号分隔）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Integer> delete(@PathVariable("ids") String[] ids) {
        log.info("[UserController.delete] ids={}", Arrays.toString(ids));
        return ResponseWrapper.success(userService.deleteByIds(Arrays.asList(ids)));
    }

    // ------------------------------------------------------------------ 密码（/me/password 字面段必须先于 /{ids}/xxx）

    @PutMapping("/me/password")
    @Operation(summary = "当前用户修改密码")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> changeMyPassword(@Valid @RequestBody ChangeMyPasswordReqDTO req) {
        String currentUserId = currentUserApi.getCurrentEmpId();
        log.info("[UserController.changeMyPassword] userId={}", currentUserId);
        userService.changeMyPassword(currentUserId, req);
        return ResponseWrapper.success();
    }

    @PutMapping("/{userId}/password")
    @Operation(summary = "管理员为指定用户设置新密码")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> setPasswordByAdmin(
            @PathVariable("userId") String userId,
            @Valid @RequestBody com.bank.branch.platform.auth.api.dto.AdminSetPasswordReqDTO req) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.setPasswordByAdmin] userId={} operator={}", userId, operator);
        userService.setPasswordByAdmin(userId, req.getNewPassword(), operator);
        return ResponseWrapper.success();
    }

    @PutMapping("/{ids}/reset")
    @Operation(summary = "批量重置密码（默认值）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> resetPassword(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[UserController.resetPassword] ids={} operator={}", Arrays.toString(ids), operator);
        return ResponseWrapper.success(userService.resetPassword(Arrays.asList(ids), operator));
    }

    // ------------------------------------------------------------------ 批量状态

    @PutMapping("/{ids}/active")
    @Operation(summary = "批量启用")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> active(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchActivate(Arrays.asList(ids), operator));
    }

    @PutMapping("/{ids}/inactive")
    @Operation(summary = "批量禁用")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> inactive(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchInactivate(Arrays.asList(ids), operator));
    }

    @PutMapping("/{ids}/lock")
    @Operation(summary = "批量锁定")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> lock(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchLock(Arrays.asList(ids), operator));
    }

    @PutMapping("/{ids}/unlock")
    @Operation(summary = "批量解锁")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Integer> unlock(@PathVariable("ids") String[] ids) {
        String operator = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(userService.batchUnlock(Arrays.asList(ids), operator));
    }
}
