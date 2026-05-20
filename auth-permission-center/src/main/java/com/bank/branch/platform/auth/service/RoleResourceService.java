package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.event.PermissionCacheInvalidatedEvent;
import com.bank.branch.platform.auth.entity.PtRoleResource;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 角色资源绑定管理服务
 * 负责角色与资源之间的增量绑定（bindResources）和全量替换（replaceResources）操作。
 * 所有写操作完成后须清除角色资源缓存并发布权限缓存失效事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleResourceService {

    private final RoleResourceMapper roleResourceMapper;
    private final RoleMapper roleMapper;
    private final ResourceMapper resourceMapper;
    private final PermissionCacheService cacheService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 增量绑定资源到指定角色（幂等：已绑定的资源跳过）
     *
     * @param roleId      角色ID
     * @param resourceIds 要绑定的资源ID列表
     * @param reason      操作原因（审计用）
     */
    @Transactional
    public void bindResources(String roleId, List<String> resourceIds, String reason) {
        if (roleMapper.selectByRoleId(roleId) == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND.getCode(),
                AuthErrorCode.ROLE_NOT_FOUND.getMessage());
        }
        int inserted = 0;
        for (String resourceId : resourceIds) {
            // 幂等：已绑定的记录跳过
            if (roleResourceMapper.existsByRoleIdAndResourceId(roleId, resourceId)) {
                log.debug("[RoleResourceService.bindResources] 已绑定，跳过 roleId={}, resourceId={}", roleId, resourceId);
                continue;
            }
            PtRoleResource rr = new PtRoleResource();
            rr.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            rr.setRoleId(roleId);
            rr.setResourceId(resourceId);
            roleResourceMapper.insert(rr);
            inserted++;
        }
        cacheService.evictRoleResourceCache(roleId);
        publishCacheInvalidatedEvent(roleId, reason);
        log.info("[RoleResourceService.bindResources] 绑定完成 roleId={}, inserted={}", roleId, inserted);
    }

    /**
     * 全量替换角色的资源绑定（先删后插，事务保证原子性）
     *
     * @param roleId      角色ID
     * @param resourceIds 替换后的资源ID列表（空列表表示清空所有绑定）
     * @param reason      操作原因（审计用）
     */
    @Transactional
    public void replaceResources(String roleId, List<String> resourceIds, String reason) {
        if (roleMapper.selectByRoleId(roleId) == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND.getCode(),
                AuthErrorCode.ROLE_NOT_FOUND.getMessage());
        }
        // 先删：清空现有绑定
        roleResourceMapper.deleteByRoleId(roleId);
        // 后插：写入新绑定
        for (String resourceId : resourceIds) {
            PtRoleResource rr = new PtRoleResource();
            rr.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            rr.setRoleId(roleId);
            rr.setResourceId(resourceId);
            roleResourceMapper.insert(rr);
        }
        cacheService.evictRoleResourceCache(roleId);
        publishCacheInvalidatedEvent(roleId, reason);
        log.info("[RoleResourceService.replaceResources] 全量替换完成 roleId={}, count={}", roleId, resourceIds.size());
    }

    /**
     * 查询角色已授权的资源ID集合（读取缓存，缓存未命中则回查数据库）
     *
     * @param roleId 角色ID
     * @return 资源ID集合
     */
    public Set<String> getResourceIdsByRoleId(String roleId) {
        return cacheService.getResourceIdsByRoleId(roleId);
    }

    /**
     * 查询角色已绑定的"菜单"资源ID列表（仅 PT_RESOURCE.IS_MENU=1 部分）。
     * <p>用于角色管理页面"分配菜单"对话框回显，与接口资源（IS_MENU=0）分开取。</p>
     *
     * @param roleId 角色ID
     * @return 菜单资源ID列表
     */
    public List<String> getMenuIdsByRoleId(String roleId) {
        return roleResourceMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 全量替换角色的"菜单"绑定（先删后插，事务保证原子性）。
     * <p>只动 PT_RESOURCE.IS_MENU=1 部分的绑定，接口资源绑定（IS_MENU=0）保持不动。
     * 与 replaceResources 区分：后者会清空 role 所有绑定（含菜单+接口）。</p>
     *
     * @param roleId  角色ID
     * @param menuIds 替换后的菜单ID列表（空列表表示清空该角色所有菜单绑定）
     * @param reason  操作原因（审计用）
     */
    @Transactional
    public void replaceMenus(String roleId, List<String> menuIds, String reason) {
        if (roleMapper.selectByRoleId(roleId) == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND.getCode(),
                AuthErrorCode.ROLE_NOT_FOUND.getMessage());
        }
        // 先删该角色所有菜单绑定（接口绑定不动）
        roleResourceMapper.deleteMenuBindingsByRoleId(roleId);
        // 后插新菜单绑定
        for (String menuId : menuIds) {
            PtRoleResource rr = new PtRoleResource();
            rr.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            rr.setRoleId(roleId);
            rr.setResourceId(menuId);
            roleResourceMapper.insert(rr);
        }
        cacheService.evictRoleResourceCache(roleId);
        publishCacheInvalidatedEvent(roleId, reason);
        log.info("[RoleResourceService.replaceMenus] 菜单分配完成 roleId={}, count={}", roleId, menuIds.size());
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private void publishCacheInvalidatedEvent(String roleId, String reason) {
        PermissionCacheInvalidatedEvent event = new PermissionCacheInvalidatedEvent();
        event.setChangeType("ROLE_RESOURCE");
        event.setAffectedRoleIds(Set.of(roleId));
        event.setOperator("SYSTEM");
        event.setReason(reason);
        event.setEventId("evt_" + System.currentTimeMillis());
        event.setOccurredAt(System.currentTimeMillis());
        eventPublisher.publishEvent(event);
    }
}
