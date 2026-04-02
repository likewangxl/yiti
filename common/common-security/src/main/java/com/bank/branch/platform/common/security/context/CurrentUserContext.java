package com.bank.branch.platform.common.security.context;

import java.util.Set;

/**
 * 当前登录用户上下文
 * 封装当前请求的用户身份信息，包括员工ID、主机构、角色集合等
 *
 * @param empId              员工ID
 * @param mainOrgCode        主机构编码
 * @param roleIds            角色ID集合
 * @param roleCodes          角色编码集合（ROLE_CODE）
 * @param candidateGroupKeys 候选组标识集合（用于工作流）
 * @param systemAdmin        是否系统管理员
 */
public record CurrentUserContext(
    String empId,
    String mainOrgCode,
    Set<String> roleIds,
    Set<String> roleCodes,
    Set<String> candidateGroupKeys,
    boolean systemAdmin
) {
    /**
     * 判断当前用户是否拥有指定角色
     *
     * @param roleId 角色ID
     * @return 如果拥有该角色返回 true
     */
    public boolean hasRole(String roleId) {
        return roleIds != null && roleIds.contains(roleId);
    }

    /**
     * 判断当前用户是否拥有目标角色集合中的任意一个角色
     *
     * @param targetRoleIds 目标角色ID集合
     * @return 如果拥有任意一个目标角色返回 true
     */
    public boolean hasAnyRole(Set<String> targetRoleIds) {
        if (roleIds == null || targetRoleIds == null) return false;
        return targetRoleIds.stream().anyMatch(roleIds::contains);
    }
}
