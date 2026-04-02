package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限缓存管理服务
 * 负责 Redis 缓存的读取、写入和失效。缓存 TTL 均为 5 分钟（与权限变更生效窗口对齐）。
 * Fail Close：缓存不可用时不允许放行，调用方需处理 RedisException。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_USER_ROLES = "auth:user-roles:";
    private static final String KEY_ROLE_RESOURCE = "auth:role-resource:";
    private static final String KEY_BIZ_SCOPE = "auth:biz-scope:";
    private static final String KEY_RESOURCE_ALL = "auth:resource:all";
    private static final String KEY_ORG_SUBTREE = "auth:org-subtree:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleBizScopeMapper roleBizScopeMapper;

    // ── 缓存读取（Cache-Aside 模式）──────────────────────────────

    /**
     * 获取用户的角色ID集合（缓存优先，Miss 时查库并回填缓存）
     *
     * @param empId 用户工号
     * @return 角色ID集合
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRoleIdsByEmpId(String empId) {
        String key = KEY_USER_ROLES + empId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Set) {
            return (Set<String>) cached;
        }
        List<String> ids = userRoleMapper.selectRoleIdsByUserId(empId);
        Set<String> result = new HashSet<>(ids);
        redisTemplate.opsForValue().set(key, result, TTL);
        return result;
    }

    /**
     * 获取角色绑定的资源ID集合（缓存优先，Miss 时查库并回填缓存）
     *
     * @param roleId 角色ID
     * @return 资源ID集合
     */
    @SuppressWarnings("unchecked")
    public Set<String> getResourceIdsByRoleId(String roleId) {
        String key = KEY_ROLE_RESOURCE + roleId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Set) {
            return (Set<String>) cached;
        }
        List<String> ids = roleResourceMapper.selectResourceIdsByRoleId(roleId);
        Set<String> result = new HashSet<>(ids);
        redisTemplate.opsForValue().set(key, result, TTL);
        return result;
    }

    /**
     * 获取全量资源列表（缓存优先，Miss 时查库并回填缓存）
     *
     * @return PtResource 实体列表
     */
    @SuppressWarnings("unchecked")
    public List<PtResource> getAllResources() {
        String key = KEY_RESOURCE_ALL;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            return (List<PtResource>) cached;
        }
        // 只加载已启用的资源，所有系统
        List<PtResource> resources = resourceMapper.selectAll(0, null);
        redisTemplate.opsForValue().set(key, resources, TTL);
        return resources;
    }

    /**
     * 获取角色的 BizType->DataScope 配置列表（缓存优先，Miss 时查库并回填缓存）
     *
     * @param roleId 角色ID
     * @return PtRoleBizScope 列表
     */
    @SuppressWarnings("unchecked")
    public List<PtRoleBizScope> getBizScopesByRoleId(String roleId) {
        String key = KEY_BIZ_SCOPE + roleId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            return (List<PtRoleBizScope>) cached;
        }
        List<PtRoleBizScope> scopes = roleBizScopeMapper.selectByRoleId(roleId);
        redisTemplate.opsForValue().set(key, scopes, TTL);
        return scopes;
    }

    // ── 缓存失效 ──────────────────────────────────────────────────

    /**
     * 使指定用户的角色缓存失效
     *
     * @param empId 用户工号
     */
    public void evictUserRolesCache(String empId) {
        redisTemplate.delete(KEY_USER_ROLES + empId);
    }

    /**
     * 使指定角色的资源绑定缓存失效
     *
     * @param roleId 角色ID
     */
    public void evictRoleResourceCache(String roleId) {
        redisTemplate.delete(KEY_ROLE_RESOURCE + roleId);
    }

    /**
     * 使指定角色的 BizScope 缓存失效
     *
     * @param roleId 角色ID
     */
    public void evictBizScopeCache(String roleId) {
        redisTemplate.delete(KEY_BIZ_SCOPE + roleId);
    }

    /**
     * 使全量资源缓存失效
     */
    public void evictAllResourceCache() {
        redisTemplate.delete(KEY_RESOURCE_ALL);
    }

    /**
     * 使机构子树缓存失效
     *
     * @param orgCode 机构编码
     */
    public void evictOrgSubtreeCache(String orgCode) {
        redisTemplate.delete(KEY_ORG_SUBTREE + orgCode);
    }
}
