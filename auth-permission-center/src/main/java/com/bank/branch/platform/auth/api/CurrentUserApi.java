package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.common.security.context.CurrentUserContext;

import java.util.Set;

/**
 * 当前用户上下文对外API
 * 所有方法均从 ThreadLocal 读取，无数据库IO，可在任意模块中调用
 */
public interface CurrentUserApi {

    /**
     * 获取完整的当前用户上下文
     *
     * @return 当前用户上下文
     */
    CurrentUserContext getCurrentUserContext();

    /**
     * 获取当前用户员工ID
     *
     * @return 员工ID
     */
    String getCurrentEmpId();

    /**
     * 获取当前用户主机构编码
     *
     * @return 主机构编码
     */
    String getCurrentOrgCode();

    /**
     * 获取当前用户角色ID集合
     *
     * @return 角色ID集合
     */
    Set<String> getCurrentRoleIds();

    /**
     * 获取当前用户角色编码集合
     *
     * @return 角色编码集合
     */
    Set<String> getCurrentRoleCodes();

    /**
     * 获取当前用户候选组标识集合（用于工作流任务认领）
     *
     * @return 候选组标识集合
     */
    Set<String> getCurrentCandidateGroupKeys();

    /**
     * 判断当前用户是否为系统管理员
     *
     * @return true 表示系统管理员，拥有全部数据范围
     */
    boolean isSystemAdmin();
}
