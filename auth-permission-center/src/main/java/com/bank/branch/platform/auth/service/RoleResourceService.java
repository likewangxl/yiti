package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.event.PermissionCacheInvalidatedEvent;
import com.bank.branch.platform.auth.entity.PtRoleResource;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
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
    private final RoleBizScopeMapper roleBizScopeMapper;
    private final PermissionCacheService cacheService;
    private final BizScopeService bizScopeService;
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
     * 全量替换角色的"菜单"绑定 + 联动重建接口绑定（先删后插，事务保证原子性）。
     * <p>语义：</p>
     * <ul>
     *   <li>删除该角色所有菜单绑定（ISMENU=1）+ 所有接口绑定（ISMENU=0）；</li>
     *   <li>按 menuIds 插入新菜单绑定；</li>
     *   <li>menuIds 非空时：联动绑「所选菜单下挂的接口」+「所有公共接口（PARENT_RESOURCE_ID 为空）」；</li>
     *   <li>menuIds 空时：所有接口/菜单都已清，角色退回"无任何资源"。</li>
     * </ul>
     * <p>关系定义在 PT_RESOURCE.PARENT_RESOURCE_ID 字段：接口资源指向所属菜单。
     * 没填 PARENT_RESOURCE_ID 的接口视为"公共基础接口"，只要分配 ≥1 菜单就附带。</p>
     * <p>管理员仍可在「权限配置」页面用 replaceResources 单独调整接口绑定；
     * 但下一次 replaceMenus 时这些手工调整会被覆盖。</p>
     *
     * @param roleId  角色ID
     * @param menuIds 替换后的菜单ID列表（空列表表示清空该角色所有菜单+接口绑定）
     * @param reason  操作原因（审计用）
     */
    @Transactional
    public void replaceMenus(String roleId, List<String> menuIds, String reason) {
        if (roleMapper.selectByRoleId(roleId) == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND.getCode(),
                AuthErrorCode.ROLE_NOT_FOUND.getMessage());
        }
        // 1. 清菜单 + 清接口绑定（接口要按新菜单重建，旧绑定全清）
        roleResourceMapper.deleteMenuBindingsByRoleId(roleId);
        roleResourceMapper.deleteInterfaceBindingsByRoleId(roleId);

        // 2. 插新菜单绑定
        for (String menuId : menuIds) {
            insertBinding(roleId, menuId);
        }

        // 3. 联动绑接口（menuIds 非空才绑；空菜单等于"无任何资源"）
        int interfaceCount = 0;
        if (!menuIds.isEmpty()) {
            // 3a. 所选菜单下挂的接口
            List<String> menuInterfaceIds = resourceMapper.selectInterfaceIdsByMenuIds(menuIds);
            for (String resId : menuInterfaceIds) {
                insertBinding(roleId, resId);
            }
            // 3b. 公共基础接口（PARENT_RESOURCE_ID 为空的接口，所有有菜单的角色都附带）
            List<String> publicInterfaceIds = resourceMapper.selectPublicInterfaceIds();
            for (String resId : publicInterfaceIds) {
                insertBinding(roleId, resId);
            }
            interfaceCount = menuInterfaceIds.size() + publicInterfaceIds.size();
        }

        // 4. 自动配默认数据范围（SELF）—— 仅在该 bizType 还没配置时新增，已配置的绝不覆盖。
        //    历史 bug：原代码直接调 saveBizScope，但它是 upsert（已存在时 updateById 强制覆盖）。
        //    导致管理员在权限配置页手工把 REPORT 调成 ORG_SUBTREE 后，下次分配菜单会被改回 SELF。
        String[] defaultBizTypes = {"REPORT", "PERF_CONFIG", "SYS_CONFIG", "NAV", "LEAD", "CUSTOMER", "LOAN", "SUPPORT"};
        for (String bizType : defaultBizTypes) {
            if (roleBizScopeMapper.selectByRoleIdAndBizType(roleId, bizType) != null) {
                continue;  // 已存在不动
            }
            try {
                bizScopeService.saveBizScope(roleId, bizType, "SELF", "菜单分配自动配置");
            } catch (Exception e) {
                log.debug("[replaceMenus] 默认 BizScope 写入失败跳过 roleId={}, bizType={}", roleId, bizType);
            }
        }

        cacheService.evictRoleResourceCache(roleId);
        publishCacheInvalidatedEvent(roleId, reason);
        log.info("[RoleResourceService.replaceMenus] 完成 roleId={}, menus={}, 联动接口={}",
                roleId, menuIds.size(), interfaceCount);
    }

    private void insertBinding(String roleId, String resourceId) {
        PtRoleResource rr = new PtRoleResource();
        rr.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        rr.setRoleId(roleId);
        rr.setResourceId(resourceId);
        roleResourceMapper.insert(rr);
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
