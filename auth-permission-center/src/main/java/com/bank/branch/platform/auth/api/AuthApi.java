package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.common.security.context.CurrentUserContext;

/**
 * 认证服务对外API
 * 提供当前登录用户上下文获取、认证状态查询和Session失效能力
 * 由 auth-permission-center 实现，可被所有业务模块依赖
 */
public interface AuthApi {

    /**
     * 获取当前登录用户上下文
     * 从 ThreadLocal 中读取，无数据库IO
     *
     * @return 当前用户上下文
     * @throws com.bank.branch.platform.common.security.exception.AuthException 未登录时抛出 AUTH-40105
     */
    CurrentUserContext getCurrentUser();

    /**
     * 判断当前请求是否已认证
     *
     * @return true 表示已登录
     */
    boolean isAuthenticated();

    /**
     * 强制使指定用户的所有 Session 失效
     * 高危操作，调用方需拥有 PERMISSION_CHANGE 权限
     *
     * @param empId 目标员工ID
     */
    void invalidateSession(String empId);
}
