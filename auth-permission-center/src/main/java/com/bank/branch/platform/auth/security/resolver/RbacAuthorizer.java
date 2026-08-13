package com.bank.branch.platform.auth.security.resolver;

import com.bank.branch.platform.auth.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * RBAC 角色资源授权校验器
 * 判断指定员工（通过其角色集合）是否拥有访问目标资源的权限。
 * 采用 "任意角色满足即通过" 的并集策略，与 DataScope 合并策略一致。
 * 系统管理员（SYS_ADMIN）的绕过逻辑由调用方（AuthorizationInterceptor）处理。
 */
@Component
@RequiredArgsConstructor
public class RbacAuthorizer {

    private final PermissionCacheService cacheService;

    /**
     * 校验员工是否拥有访问指定资源的 RBAC 权限
     * 遍历员工的所有角色，任意角色包含目标资源即视为有权限。
     *
     * @param empId      员工ID
     * @param resourceId 目标资源ID
     * @return true 表示有权限
     */
    public boolean authorize(String empId, String resourceId) {
        // 按数据库中的全部有效角色并集解析授权
        Set<String> roleIds = cacheService.getEffectiveRoleIds(empId);
        for (String roleId : roleIds) {
            Set<String> resourceIds = cacheService.getResourceIdsByRoleId(roleId);
            if (resourceIds.contains(resourceId)) {
                return true;
            }
        }
        return false;
    }
}
