package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.RoleCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.auth.api.dto.RoleUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleUserRespDTO;
import com.bank.branch.platform.auth.service.RoleService;
import com.bank.branch.platform.auth.service.UserRoleService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理控制器
 * 提供角色的增删改查及角色用户列表查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/roles")
@Tag(name = "角色管理", description = "系统角色的增删改查管理")
public class RoleController {

    private final RoleService roleService;
    private final UserRoleService userRoleService;

    /**
     * 分页查询角色列表
     *
     * @param keyword      关键字（角色名或编码模糊匹配），可为 null
     * @param recordStatus 状态过滤，可为 null
     * @param pageNo       页码，默认 1
     * @param pageSize     每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/")
    @Operation(summary = "分页查询角色列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<RoleRespDTO> listRoles(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "recordStatus", required = false) Integer recordStatus,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[RoleController.listRoles] keyword={}, recordStatus={}, pageNo={}, pageSize={}",
                keyword, recordStatus, pageNo, pageSize);
        PageResult<RoleRespDTO> result = roleService.listByPage(keyword, recordStatus, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 新增角色
     *
     * @param req 角色创建请求DTO
     * @return 新建角色DTO
     */
    @PostMapping("/")
    @Operation(summary = "新增角色")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<RoleRespDTO> createRole(@Valid @RequestBody RoleCreateReqDTO req) {
        log.info("[RoleController.createRole] roleCode={}", req.getRoleCode());
        RoleRespDTO dto = roleService.createRole(req.getRoleCode(), req.getRoleChName(), req.getRemark());
        return ResponseWrapper.success(dto);
    }

    /**
     * 更新角色信息
     *
     * @param roleId 角色ID（路径参数）
     * @param req    角色更新请求DTO
     * @return 更新后的角色DTO
     */
    @PutMapping("/{roleId}")
    @Operation(summary = "更新角色信息")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<RoleRespDTO> updateRole(
            @PathVariable("roleId") String roleId,
            @RequestBody RoleUpdateReqDTO req) {
        log.info("[RoleController.updateRole] roleId={}", roleId);
        RoleRespDTO dto = roleService.updateRole(roleId, req.getRoleChName(), req.getRemark());
        return ResponseWrapper.success(dto);
    }

    /**
     * 删除角色（逻辑删除）
     *
     * @param roleId 角色ID（路径参数）
     * @param reason 删除原因（审计用）
     * @return 成功响应
     */
    @DeleteMapping("/{roleId}")
    @Operation(summary = "删除角色")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> deleteRole(
            @PathVariable("roleId") String roleId,
            @RequestParam("reason") String reason) {
        log.info("[RoleController.deleteRole] roleId={}, reason={}", roleId, reason);
        roleService.deleteRole(roleId, reason);
        return ResponseWrapper.success();
    }

    /**
     * 分页查询角色下的用户列表
     *
     * @param roleId   角色ID（路径参数）
     * @param keyword  关键字（工号/姓名），可为 null
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/{roleId}/users")
    @Operation(summary = "查询角色下的用户列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<RoleUserRespDTO> listRoleUsers(
            @PathVariable("roleId") String roleId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[RoleController.listRoleUsers] roleId={}, keyword={}", roleId, keyword);
        PageResult<RoleUserRespDTO> result = userRoleService.listRoleUsers(roleId, keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
