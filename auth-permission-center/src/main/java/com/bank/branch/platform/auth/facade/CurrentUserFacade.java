package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 当前用户上下文 Facade 实现
 * 实现 CurrentUserApi，所有方法均从 ThreadLocal 读取，无数据库 IO。
 * 可在任意业务模块中安全调用，调用前须确保请求已通过认证过滤器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserFacade implements CurrentUserApi {

    private final CurrentUserProvider currentUserProvider;

    /**
     * 获取完整的当前用户上下文
     * ThreadLocal 中无数据时表示未登录，抛出 AUTH-40105
     *
     * @return 当前用户上下文（非 null）
     * @throws AuthException AUTH-40105 未登录或会话已过期时
     */
    @Override
    public CurrentUserContext getCurrentUserContext() {
        CurrentUserContext ctx = currentUserProvider.get();
        if (ctx == null) {
            throw new AuthException("AUTH-40105", "未登录或会话已过期");
        }
        return ctx;
    }

    /**
     * 获取当前用户员工ID
     *
     * @return 员工ID
     * @throws AuthException AUTH-40105 未登录时
     */
    @Override
    public String getCurrentEmpId() {
        return getCurrentUserContext().empId();
    }

    /**
     * 获取当前用户主机构编码
     *
     * @return 主机构编码
     * @throws AuthException AUTH-40105 未登录时
     */
    @Override
    public String getCurrentOrgCode() {
        return getCurrentUserContext().mainOrgCode();
    }

    /**
     * 获取当前用户角色ID集合
     * 若用户未绑定任何角色，返回空集合
     *
     * @return 角色ID集合（不为 null）
     * @throws AuthException AUTH-40105 未登录时
     */
    @Override
    public Set<String> getCurrentRoleIds() {
        CurrentUserContext ctx = getCurrentUserContext();
        return ctx.roleIds() != null ? ctx.roleIds() : Set.of();
    }

    /**
     * 获取当前用户角色编码集合
     * 若用户未绑定任何角色，返回空集合
     *
     * @return 角色编码集合（不为 null）
     * @throws AuthException AUTH-40105 未登录时
     */
    @Override
    public Set<String> getCurrentRoleCodes() {
        CurrentUserContext ctx = getCurrentUserContext();
        return ctx.roleCodes() != null ? ctx.roleCodes() : Set.of();
    }

    /**
     * 获取当前用户候选组标识集合（用于工作流任务认领）
     * 若用户未绑定任何角色，返回空集合
     *
     * @return 候选组标识集合（不为 null）
     * @throws AuthException AUTH-40105 未登录时
     */
    @Override
    public Set<String> getCurrentCandidateGroupKeys() {
        CurrentUserContext ctx = getCurrentUserContext();
        return ctx.candidateGroupKeys() != null ? ctx.candidateGroupKeys() : Set.of();
    }

    /**
     * 判断当前用户是否为系统管理员
     * 系统管理员拥有全部数据范围，跳过细粒度权限校验
     *
     * @return true 表示系统管理员
     * @throws AuthException AUTH-40105 未登录时
     */
    @Override
    public boolean isSystemAdmin() {
        return getCurrentUserContext().systemAdmin();
    }
}
