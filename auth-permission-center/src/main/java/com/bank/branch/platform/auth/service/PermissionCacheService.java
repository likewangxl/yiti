package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限缓存管理服务（去 Redis 后改直接读 DB）。
 * <p>原 Cache-Aside 5 分钟 TTL 已移除：行内多实例环境无 Redis 可用，
 * 改为各实例独立查 DB。PT_USER_ROLE/PT_ROLE_RESOURCE 主键索引点查 ~0.1ms，
 * 业务 SQL 本身 10ms+，缓存层去掉对热路径性能无感。</p>
 * <p>evictXxxCache 系列方法保留签名作 NoOp，避免业务层（RoleService/UserRoleService
 * /BizScopeService/RoleResourceService）大量调用点改造。后期发现热点可改回
 * Caffeine 本地缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleBizScopeMapper roleBizScopeMapper;

    // ── 直接读 DB（去 Redis） ─────────────────────────────────────

    /** 获取用户的角色ID集合 */
    public Set<String> getRoleIdsByEmpId(String empId) {
        return new HashSet<>(userRoleMapper.selectRoleIdsByUserId(empId));
    }

    /** 获取角色绑定的资源ID集合 */
    public Set<String> getResourceIdsByRoleId(String roleId) {
        return new HashSet<>(roleResourceMapper.selectResourceIdsByRoleId(roleId));
    }

    /** 获取全量资源列表（状态=0 启用，全系统） */
    public List<PtResource> getAllResources() {
        return resourceMapper.selectAll(0, null);
    }

    /** 获取角色的 BizType→DataScope 配置列表 */
    public List<PtRoleBizScope> getBizScopesByRoleId(String roleId) {
        return roleBizScopeMapper.selectByRoleId(roleId);
    }

    // ── 缓存失效系列：NoOp 保留签名（去 Redis 后无缓存可清，业务层零改动） ─

    public void evictUserRolesCache(String empId) {
        log.debug("[PermissionCacheService.evictUserRolesCache] NoOp post-redis empId={}", empId);
    }

    public void evictRoleResourceCache(String roleId) {
        log.debug("[PermissionCacheService.evictRoleResourceCache] NoOp post-redis roleId={}", roleId);
    }

    public void evictBizScopeCache(String roleId) {
        log.debug("[PermissionCacheService.evictBizScopeCache] NoOp post-redis roleId={}", roleId);
    }

    public void evictAllResourceCache() {
        log.debug("[PermissionCacheService.evictAllResourceCache] NoOp post-redis");
    }

    public void evictOrgSubtreeCache(String orgCode) {
        log.debug("[PermissionCacheService.evictOrgSubtreeCache] NoOp post-redis orgCode={}", orgCode);
    }
}
