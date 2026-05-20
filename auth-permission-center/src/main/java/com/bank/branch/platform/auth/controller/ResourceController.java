package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.ResourceCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO;
import com.bank.branch.platform.auth.api.dto.ResourceUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleMenuReplaceReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleResourceBindReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleResourceReplaceReqDTO;
import com.bank.branch.platform.auth.service.ResourceService;
import com.bank.branch.platform.auth.service.RoleResourceService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 资源管理控制器
 * 提供资源树查询、资源 CRUD 以及角色资源绑定管理接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "资源管理", description = "系统资源及角色资源绑定管理")
public class ResourceController {

    private final ResourceService resourceService;
    private final RoleResourceService roleResourceService;

    /**
     * 获取资源树
     *
     * @param status  资源状态过滤，可为 null
     * @param sysCode 系统编号过滤，可为 null
     * @return 资源树根节点列表
     */
    @GetMapping("/resources/tree")
    @Operation(summary = "获取资源树")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<ResourceTreeNodeDTO>> getResourceTree(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "sysCode", required = false) String sysCode) {
        log.debug("[ResourceController.getResourceTree] status={}, sysCode={}", status, sysCode);
        List<ResourceTreeNodeDTO> tree = resourceService.getResourceTree(status, sysCode);
        return ResponseWrapper.success(tree);
    }

    /**
     * 新增资源
     *
     * @param req 资源创建请求DTO
     * @return 新建资源DTO
     */
    @PostMapping("/resources")
    @Operation(summary = "新增资源")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<ResourceTreeNodeDTO> createResource(@Valid @RequestBody ResourceCreateReqDTO req) {
        log.info("[ResourceController.createResource] url={}, method={}", req.getResourceUrl(), req.getResourceMethod());
        ResourceTreeNodeDTO dto = resourceService.createResource(
                req.getResourceUrl(),
                req.getResourceMethod(),
                req.getMenuName(),
                req.getIsMenu(),
                req.getMenuEndFlag(),
                req.getMenuRankNo(),
                req.getParentResourceId(),
                req.getSysCode());
        return ResponseWrapper.success(dto);
    }

    /**
     * 更新资源信息
     *
     * @param resourceId 资源ID（路径参数）
     * @param req        资源更新请求DTO
     * @return 更新后的资源DTO
     */
    @PutMapping("/resources/{resourceId}")
    @Operation(summary = "更新资源信息")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<ResourceTreeNodeDTO> updateResource(
            @PathVariable("resourceId") String resourceId,
            @RequestBody ResourceUpdateReqDTO req) {
        log.info("[ResourceController.updateResource] resourceId={}", resourceId);
        ResourceTreeNodeDTO dto = resourceService.updateResource(resourceId, req);
        return ResponseWrapper.success(dto);
    }

    /**
     * 删除资源（逻辑删除）
     *
     * @param resourceId 资源ID（路径参数）
     * @param reason     删除原因（审计用）
     * @return 成功响应
     */
    @DeleteMapping("/resources/{resourceId}")
    @Operation(summary = "删除资源")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> deleteResource(
            @PathVariable("resourceId") String resourceId,
            @RequestParam("reason") String reason) {
        log.info("[ResourceController.deleteResource] resourceId={}, reason={}", resourceId, reason);
        resourceService.deleteResource(resourceId, reason);
        return ResponseWrapper.success();
    }

    /**
     * 查询角色已授权的资源ID集合
     *
     * @param roleId 角色ID（路径参数）
     * @return 资源ID集合
     */
    @GetMapping("/roles/{roleId}/resources")
    @Operation(summary = "查询角色已授权资源ID集合")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<Set<String>> getResourceIdsByRoleId(@PathVariable("roleId") String roleId) {
        log.debug("[ResourceController.getResourceIdsByRoleId] roleId={}", roleId);
        Set<String> ids = roleResourceService.getResourceIdsByRoleId(roleId);
        return ResponseWrapper.success(ids);
    }

    /**
     * 增量绑定资源到角色
     *
     * @param roleId 角色ID（路径参数）
     * @param req    绑定请求DTO（含资源ID列表和操作原因）
     * @return 成功响应
     */
    @PostMapping("/roles/{roleId}/resources")
    @Operation(summary = "增量绑定资源到角色")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> bindResources(
            @PathVariable("roleId") String roleId,
            @Valid @RequestBody RoleResourceBindReqDTO req) {
        log.info("[ResourceController.bindResources] roleId={}, resourceIds={}", roleId, req.getResourceIds());
        roleResourceService.bindResources(roleId, req.getResourceIds(), req.getReason());
        return ResponseWrapper.success();
    }

    /**
     * 全量替换角色的资源绑定
     *
     * @param roleId 角色ID（路径参数）
     * @param req    替换请求DTO（含新资源ID列表和操作原因）
     * @return 成功响应
     */
    @PutMapping("/roles/{roleId}/resources")
    @Operation(summary = "全量替换角色资源绑定")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> replaceResources(
            @PathVariable("roleId") String roleId,
            @Valid @RequestBody RoleResourceReplaceReqDTO req) {
        log.info("[ResourceController.replaceResources] roleId={}, resourceIds={}", roleId, req.getResourceIds());
        roleResourceService.replaceResources(roleId, req.getResourceIds(), req.getReason());
        return ResponseWrapper.success();
    }

    // ─── 菜单分配（参考 xanpd role.vue 分配菜单流程） ──────────────────────

    /**
     * 获取菜单树（PT_RESOURCE.IS_MENU=1 的全量菜单层级），供"分配菜单"对话框使用。
     */
    @GetMapping("/resources/menu-tree")
    @Operation(summary = "获取菜单树")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<ResourceTreeNodeDTO>> getMenuTree() {
        log.debug("[ResourceController.getMenuTree]");
        return ResponseWrapper.success(resourceService.getMenuTree());
    }

    /**
     * 查询角色已绑定的菜单ID列表（仅 IS_MENU=1 部分），用于"分配菜单"对话框回显勾选。
     */
    @GetMapping("/roles/{roleId}/menus")
    @Operation(summary = "查询角色已绑菜单ID列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<String>> getRoleMenuIds(@PathVariable("roleId") String roleId) {
        log.debug("[ResourceController.getRoleMenuIds] roleId={}", roleId);
        return ResponseWrapper.success(roleResourceService.getMenuIdsByRoleId(roleId));
    }

    /**
     * 全量替换角色的菜单绑定（仅 IS_MENU=1 部分），接口绑定不动。
     */
    @PutMapping("/roles/{roleId}/menus")
    @Operation(summary = "全量替换角色菜单绑定")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.PERMISSION_CHANGE)
    public ResponseWrapper<Void> replaceMenus(
            @PathVariable("roleId") String roleId,
            @Valid @RequestBody RoleMenuReplaceReqDTO req) {
        log.info("[ResourceController.replaceMenus] roleId={}, menuIds={}", roleId, req.getMenuIds());
        roleResourceService.replaceMenus(roleId, req.getMenuIds(), req.getReason());
        return ResponseWrapper.success();
    }
}
