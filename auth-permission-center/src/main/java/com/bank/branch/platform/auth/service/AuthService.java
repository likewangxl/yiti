package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.AuthException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务
 * 负责登录验证、Session 管理、当前用户上下文构建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Session 中存储当前用户上下文的 Key */
    public static final String SESSION_USER_KEY = "currentUser";

    /** 密码错误次数超出此值自动锁定账号 */
    private static final int MAX_WRONG_COUNT = 5;

    private final UserMapper userMapper;
    private final UserOrgMapper userOrgMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgMapper orgMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     * 校验顺序：用户名 → 账号状态 → 密码 → 密码错误次数
     *
     * @param username 用户名
     * @param password 明文密码
     * @param session  HttpSession
     * @return LoginRespDTO
     * @throws AuthException 认证失败时抛出
     */
    @Transactional
    public LoginRespDTO login(String username, String password, HttpSession session) {
        PtUser user = userMapper.selectByUsername(username);
        if (user == null) {
            // 不暴露"用户名不存在"，统一返回 AUTH-40101
            throw new AuthException(AuthErrorCode.LOGIN_FAILED.getCode(),
                AuthErrorCode.LOGIN_FAILED.getMessage());
        }

        // 先检查账号状态
        checkAccountStatus(user);

        // 校验密码
        boolean pwdMatch = passwordEncoder.matches(password, user.getPwd());
        if (!pwdMatch) {
            int newCount = user.getPassWrongCount() + 1;
            if (newCount >= MAX_WRONG_COUNT) {
                // 锁定账号
                userMapper.updateLockedStatus(user.getUserId(), 1);
                userMapper.updatePassWrongCount(user.getUserId(), newCount);
                throw new AuthException(AuthErrorCode.PASSWORD_ATTEMPTS_EXCEEDED.getCode(),
                    AuthErrorCode.PASSWORD_ATTEMPTS_EXCEEDED.getMessage());
            }
            userMapper.updatePassWrongCount(user.getUserId(), newCount);
            throw new AuthException(AuthErrorCode.LOGIN_FAILED.getCode(),
                AuthErrorCode.LOGIN_FAILED.getMessage());
        }

        // 登录成功：重置错误次数
        userMapper.updatePassWrongCount(user.getUserId(), 0);

        // 查询用户主机构
        ExtUserOrg userOrg = userOrgMapper.selectByUserId(user.getUserId());
        String mainOrgCode = userOrg != null ? userOrg.getOrgCode() : null;
        String mainOrgName = null;
        if (mainOrgCode != null) {
            ExtOrgInfo org = orgMapper.selectByOrgCode(mainOrgCode);
            mainOrgName = org != null ? org.getOrgName() : null;
        }

        // 查询角色列表
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(user.getUserId());
        Set<String> roleIds = roles.stream().map(PtRole::getRoleId).collect(Collectors.toSet());
        Set<String> roleCodes = roles.stream().map(PtRole::getRoleCode).collect(Collectors.toSet());
        // 候选组 Key 格式：ROLE:{ROLE_CODE}
        Set<String> candidateGroupKeys = roleCodes.stream()
            .map(c -> "ROLE:" + c).collect(Collectors.toSet());
        boolean isAdmin = roleCodes.contains("SYS_ADMIN");

        // 构建 CurrentUserContext 并存入 Session
        CurrentUserContext userCtx = new CurrentUserContext(
            user.getUserId(), mainOrgCode, roleIds, roleCodes, candidateGroupKeys, isAdmin);
        session.setAttribute(SESSION_USER_KEY, userCtx);

        // 构建响应
        LoginRespDTO resp = new LoginRespDTO();
        resp.setEmpId(user.getUserId());
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getUserchnname());
        resp.setMainOrgCode(mainOrgCode);
        resp.setMainOrgName(mainOrgName);
        resp.setToken(session.getId());
        resp.setRoles(roles.stream().map(r -> {
            RoleSimpleDTO dto = new RoleSimpleDTO();
            dto.setRoleId(r.getRoleId());
            dto.setRoleCode(r.getRoleCode());
            dto.setRoleChName(r.getRoleChName());
            return dto;
        }).collect(Collectors.toList()));

        log.info("[AuthService.login] 登录成功 empId={}, roles={}", user.getUserId(), roleCodes);
        return resp;
    }

    /**
     * 用户登出，销毁当前 Session
     *
     * @param session HttpSession
     */
    public void logout(HttpSession session) {
        session.invalidate();
        log.info("[AuthService.logout] Session 已销毁");
    }

    /**
     * 从 Session 中获取当前登录用户上下文
     *
     * @param session HttpSession
     * @return CurrentUserContext
     * @throws AuthException 未登录时抛出 AUTH-40105
     */
    public CurrentUserContext getCurrentUser(HttpSession session) {
        Object attr = session.getAttribute(SESSION_USER_KEY);
        if (!(attr instanceof CurrentUserContext)) {
            throw new AuthException(AuthErrorCode.NOT_AUTHENTICATED.getCode(),
                AuthErrorCode.NOT_AUTHENTICATED.getMessage());
        }
        return (CurrentUserContext) attr;
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /** 检查账号状态（锁定、过期、未启用） */
    private void checkAccountStatus(PtUser user) {
        if (user.getIsLocked() == 1) {
            throw new AuthException(AuthErrorCode.ACCOUNT_LOCKED.getCode(),
                AuthErrorCode.ACCOUNT_LOCKED.getMessage());
        }
        if (user.getIsExpired() == 1) {
            throw new AuthException(AuthErrorCode.ACCOUNT_EXPIRED.getCode(),
                AuthErrorCode.ACCOUNT_EXPIRED.getMessage());
        }
        // ISENABLED: 0=启用, 1=未启用
        if (user.getIsEnabled() == 1) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DISABLED.getCode(),
                AuthErrorCode.ACCOUNT_DISABLED.getMessage());
        }
    }
}
