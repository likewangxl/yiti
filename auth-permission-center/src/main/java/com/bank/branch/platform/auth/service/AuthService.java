package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.CheckPermissionReqDTO;
import com.bank.branch.platform.auth.api.dto.CheckPermissionRespDTO;
import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.api.dto.PermissionSetRespDTO;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.exception.AuthException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    private final PermissionCacheService cacheService;
    private final BizScopeService bizScopeService;
    private final ResourceMapper resourceMapper;
    private final CurrentUserProvider currentUserProvider;

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
        Integer orgLevel = null;
        if (mainOrgCode != null) {
            ExtOrgInfo org = orgMapper.selectByOrgCode(mainOrgCode);
            mainOrgName = org != null ? org.getOrgName() : null;
            orgLevel = org != null ? org.getOrgLevel() : null;
        }

        // 查询角色列表
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(user.getUserId());
        Set<String> roleIds = roles.stream().map(PtRole::getRoleId).collect(Collectors.toSet());
        Set<String> roleCodes = roles.stream().map(PtRole::getRoleCode).collect(Collectors.toSet());
        // 候选组 Key 格式：ROLE:{ROLE_CODE} / USER:{empId} / ORG:{mainOrgCode}
        // 三种前缀对齐 CandidateResolverService.resolveCandidates 的输出，
        // 否则 BPMN 配 USER/ORG 类型候选时该用户匹配不到 Flowable 候选组（2026-05-20 修复）
        Set<String> candidateGroupKeys = new java.util.HashSet<>();
        roleCodes.forEach(c -> candidateGroupKeys.add("ROLE:" + c));
        candidateGroupKeys.add("USER:" + user.getUserId());
        if (mainOrgCode != null) {
            candidateGroupKeys.add("ORG:" + mainOrgCode);
        }
        boolean isAdmin = roleCodes.contains("SYS_ADMIN");

        // 构建 CurrentUserContext 并存入 Session
        CurrentUserContext userCtx = new CurrentUserContext(
            user.getUserId(), user.getUsername(), user.getUserchnname(),
            mainOrgCode, mainOrgName, orgLevel,
            roleIds, roleCodes, candidateGroupKeys, isAdmin);
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

    /**
     * 获取当前用户的完整权限集合（A.6）。
     * <p>
     * 聚合用户的资源 URL 权限和业务数据范围，供前端/网关做完整权限判断。
     * </p>
     *
     * @param empId 用户工号
     * @return 用户权限集合 DTO
     */
    public PermissionSetRespDTO getUserPermissions(String empId) {
        log.debug("[AuthService.getUserPermissions] empId={}", empId);

        // 获取用户角色信息
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(empId);
        Set<String> roleIds = roles.stream().map(PtRole::getRoleId).collect(Collectors.toSet());
        Set<String> roleCodes = roles.stream().map(PtRole::getRoleCode).collect(Collectors.toSet());
        boolean isAdmin = roleCodes.contains("SYS_ADMIN");

        // 收集用户可访问的所有资源 URL（去重）
        Set<String> resourceUrls = new java.util.HashSet<>();
        for (String roleId : roleIds) {
            Set<String> resourceIds = cacheService.getResourceIdsByRoleId(roleId);
            for (String resId : resourceIds) {
                PtResource res = resourceMapper.selectByResourceId(resId);
                if (res != null && res.getStatus() != null && res.getStatus() == 0) {
                    resourceUrls.add(res.getResourceUrl());
                }
            }
        }

        // 收集用户的业务数据范围
        Map<String, String> bizScopes = bizScopeService.getUserBizScopes(empId)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> e.getValue().name()
                ));

        PermissionSetRespDTO dto = new PermissionSetRespDTO();
        dto.setResourceUrls(resourceUrls);
        dto.setBizScopes(bizScopes);
        dto.setRoleIds(roleIds);
        dto.setRoleCodes(roleCodes);
        dto.setIsSystemAdmin(isAdmin);
        return dto;
    }

    /**
     * 实时权限检查（A.7）。
     * <p>
     * 校验指定用户是否有权访问指定资源 URL + HTTP 方法。
     * 若同时传入 bizType，则额外校验数据范围写权限。
     * </p>
     *
     * @param req 权限检查请求（包含 resourceUrl、resourceMethod、bizType、action）
     * @return 权限检查结果 DTO
     */
    public CheckPermissionRespDTO checkPermission(CheckPermissionReqDTO req) {
        String empId = currentUserProvider.get().empId();
        log.debug("[AuthService.checkPermission] empId={}, url={}, method={}",
                empId, req.getResourceUrl(), req.getResourceMethod());

        CheckPermissionRespDTO result = new CheckPermissionRespDTO();
        result.setAllowed(false);

        // 1. RBAC 资源权限检查
        Set<String> roleIds = cacheService.getRoleIdsByEmpId(empId);
        boolean rbacPassed = false;
        for (String roleId : roleIds) {
            Set<String> resourceIds = cacheService.getResourceIdsByRoleId(roleId);
            for (String resId : resourceIds) {
                PtResource res = resourceMapper.selectByResourceId(resId);
                if (res == null || res.getStatus() == null || res.getStatus() != 0) continue;
                // method 为 * 时匹配所有方法
                if ("*".equals(res.getResourceMethod())
                        || res.getResourceMethod().equalsIgnoreCase(req.getResourceMethod())) {
                    // 使用 AntPathMatcher 匹配 URL
                    if (new org.springframework.util.AntPathMatcher().match(res.getResourceUrl(), req.getResourceUrl())) {
                        rbacPassed = true;
                        break;
                    }
                }
            }
            if (rbacPassed) break;
        }

        result.setRbacPassed(rbacPassed);
        if (!rbacPassed) {
            result.setDenyReason("资源权限不足（RBAC 拒绝）");
            return result;
        }

        // 2. 数据范围权限检查（仅当传入 bizType 时）
        if (req.getBizType() != null && !req.getBizType().isEmpty()) {
            try {
                BizType bizType = BizType.valueOf(req.getBizType());
                // 先尝试解析用户的数据范围：无 scope 配置时抛 PermissionDeniedException
                com.bank.branch.platform.common.security.enums.DataScopeType scopeType =
                        bizScopeService.resolveScope(empId, bizType);
                result.setScopePassed(true);
                result.setScope(scopeType.name());
            } catch (IllegalArgumentException e) {
                result.setScopePassed(false);
                result.setDenyReason("无效的 BizType: " + req.getBizType());
                return result;
            } catch (com.bank.branch.platform.common.web.exception.PermissionDeniedException e) {
                result.setScopePassed(false);
                result.setDenyReason("用户未配置该业务类型的数据范围权限: " + req.getBizType());
                return result;
            }
        } else {
            result.setScopePassed(true);
        }

        result.setAllowed(true);
        return result;
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
