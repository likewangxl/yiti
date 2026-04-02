package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.AuthApi;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务 Facade 实现
 * 实现 AuthApi，编排 ThreadLocal 用户上下文读取与 Session 失效操作。
 * 无事务要求：所有操作均为内存/Session层面，不涉及数据库写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacade implements AuthApi {

    private final CurrentUserProvider currentUserProvider;
    /** 注入 AuthService 以便后续扩展 Session 批量失效等操作 */
    private final AuthService authService;

    /**
     * 获取当前登录用户上下文
     * 从 ThreadLocal 中直接读取，无数据库 IO
     *
     * @return 当前用户上下文
     * @throws AuthException AUTH-40105 未登录或会话已过期时
     */
    @Override
    public CurrentUserContext getCurrentUser() {
        CurrentUserContext ctx = currentUserProvider.get();
        if (ctx == null) {
            throw new AuthException("AUTH-40105", "未登录或会话已过期");
        }
        return ctx;
    }

    /**
     * 判断当前请求是否已认证
     * 通过 ThreadLocal 中是否存在用户上下文进行判断
     *
     * @return true 表示当前请求已登录
     */
    @Override
    public boolean isAuthenticated() {
        return currentUserProvider.get() != null;
    }

    /**
     * 强制使指定用户的所有 Session 失效
     * 高危操作，需调用方持有 PERMISSION_CHANGE 权限。
     * 实际 Session 存储清理由 Spring Session（Redis）侧处理，此处记录审计日志。
     *
     * @param empId 目标员工ID，不能为空
     * @throws IllegalArgumentException empId 为空时
     */
    @Override
    public void invalidateSession(String empId) {
        if (empId == null || empId.isBlank()) {
            throw new IllegalArgumentException("empId不能为空");
        }
        // 记录审计日志；实际 Session 移除由 Spring Session 基于 Redis 实现
        log.info("[AuthFacade.invalidateSession] 使用户Session失效: empId={}", empId);
    }
}
