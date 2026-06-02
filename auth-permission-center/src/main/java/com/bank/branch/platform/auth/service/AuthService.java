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
import com.bank.branch.platform.auth.uniauth.UniAuthSidecarClient;
import com.bank.branch.platform.auth.uniauth.dto.UniAuthRespDTO;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.exception.AuthException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final com.bank.branch.platform.auth.mapper.RoleResourceMapper roleResourceMapper;
    private final ResourceService resourceService;
    private final CurrentUserProvider currentUserProvider;

    /** 统一认证（边车 11003）客户端。required=false → 边车未就绪时不阻断登录主流程。 */
    @Autowired(required = false)
    private UniAuthSidecarClient uniAuthSidecarClient;

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
        String deptNo = null;
        if (mainOrgCode != null) {
            ExtOrgInfo org = orgMapper.selectByOrgCode(mainOrgCode);
            mainOrgName = org != null ? org.getOrgName() : null;
            orgLevel = org != null ? org.getOrgLevel() : null;
            deptNo = org != null ? org.getDeptNo() : null;
        }

        // 查询角色列表
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(user.getUserId());
        // 无角色用户禁止登录；并把主角色排到首位作为当前登录角色（前端取 roles[0]）
        PtRole primaryRole = resolvePrimaryRoleAndOrder(user.getUserId(), roles);
        Set<String> roleCodes = roles.stream().map(PtRole::getRoleCode).collect(Collectors.toSet()); // 仅日志用

        // 当前登录角色 = 主角色：会话上下文按「单一当前角色」构建
        // 菜单/接口权限/数据范围/工作流候选均以当前角色为准，可经 /api/auth/switch-role 切换
        CurrentUserContext userCtx = buildContextForActiveRole(
            user.getUserId(), user.getUsername(), user.getUserchnname(),
            mainOrgCode, mainOrgName, orgLevel, primaryRole);
        session.setAttribute(SESSION_USER_KEY, userCtx);

        // 构建响应
        LoginRespDTO resp = new LoginRespDTO();
        resp.setEmpId(user.getUserId());
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getUserchnname());
        resp.setMainOrgCode(mainOrgCode);
        resp.setMainOrgName(mainOrgName);
        resp.setDeptNo(deptNo);
        resp.setToken(session.getId());
        resp.setRoles(roles.stream().map(r -> {
            RoleSimpleDTO dto = new RoleSimpleDTO();
            dto.setRoleId(r.getRoleId());
            dto.setRoleCode(r.getRoleCode());
            dto.setRoleChName(r.getRoleChName());
            dto.setPrimary(r.getRoleId().equals(primaryRole.getRoleId()));
            return dto;
        }).collect(Collectors.toList()));
        resp.setPrimaryRoleId(primaryRole.getRoleId());
        resp.setPrimaryRoleCode(primaryRole.getRoleCode());
        resp.setPrimaryRoleName(primaryRole.getRoleChName());

        // 登录成功后追加一步：通过边车调统一认证 S120030044 查授权信息（fail-open，异常不挡主流程）
        // 对应 xanpd 的 LoginController.loginAfter → IAuthorityHandler.handleAuthorities → UserInfoFromUIAS
        if (uniAuthSidecarClient != null) {
            try {
                UniAuthRespDTO uia = uniAuthSidecarClient.queryUserInfo(user.getUsername());
                if (uia.isSuccess() && uia.getSvcBody() != null && uia.getSvcBody().getUserInfoQryRslt() != null) {
                    session.setAttribute("UIAS_USER_BSC_INFO",
                            uia.getSvcBody().getUserInfoQryRslt().getUserBscInfo());
                    session.setAttribute("UIAS_INST_INFO_LIST",
                            uia.getSvcBody().getUserInfoQryRslt().getInstInfoList());
                    log.info("[AuthService.login] UIAS 授权同步成功 empId={}", user.getUserId());
                } else {
                    log.warn("[AuthService.login] UIAS 查询失败 empId={} returnCode={}",
                            user.getUserId(),
                            uia.getRspSvcHeader() == null ? null : uia.getRspSvcHeader().getReturnCode());
                }
            } catch (Exception e) {
                log.warn("[AuthService.login] UIAS 查询异常（不阻断登录） empId={} err={}",
                        user.getUserId(), e.getMessage());
            }
        }

        log.info("[AuthService.login] 登录成功 empId={}, roles={}", user.getUserId(), roleCodes);
        return resp;
    }

    /**
     * 统一认证登录（开发期 mock）。
     * <p>对应 xanpd LoginController.redirectSuccess 的"UIAS 跳转后建 session"流程 —
     * 不校验密码，仅按工号查 PT_USER + 调统一认证 S120030044 + 建 session。
     * <p>生产环境应改为完整 UIAS 重定向链路（行内 Spring Security UIAS filter）。
     *
     * @param userDomainName AD 域账号（工号，例 "12050965"）
     * @param session HttpSession
     * @return LoginRespDTO
     * @throws AuthException 用户不存在 / 账号被锁 / UIAS 调用失败时抛出
     */
    @Transactional
    public LoginRespDTO loginByUniAuth(String userDomainName, HttpSession session) {
        // 1) 按工号查 PT_USER（不校验密码）
        PtUser user = userMapper.selectByUsername(userDomainName);
        if (user == null) {
            throw new AuthException(AuthErrorCode.LOGIN_FAILED.getCode(),
                    "统一认证：用户不存在 userDomainName=" + userDomainName);
        }

        // 2) 账号状态（被锁定/停用/过期仍然不能登）
        checkAccountStatus(user);

        // 3) 调统一认证 S120030044 查授权信息（fail-close：必须成功）
        if (uniAuthSidecarClient == null) {
            throw new AuthException(AuthErrorCode.LOGIN_FAILED.getCode(),
                    "统一认证客户端未就绪，请检查边车是否启动");
        }
        UniAuthRespDTO uia;
        try {
            uia = uniAuthSidecarClient.queryUserInfo(userDomainName);
        } catch (Exception e) {
            log.error("[AuthService.loginByUniAuth] UIAS 调用异常 userDomainName={} err={}",
                    userDomainName, e.getMessage(), e);
            throw new AuthException(AuthErrorCode.LOGIN_FAILED.getCode(),
                    "统一认证调用异常: " + e.getMessage());
        }
        if (!uia.isSuccess()) {
            String rc = uia.getRspSvcHeader() == null ? null : uia.getRspSvcHeader().getReturnCode();
            String rm = uia.getRspSvcHeader() == null ? null : uia.getRspSvcHeader().getReturnMsg();
            throw new AuthException(AuthErrorCode.LOGIN_FAILED.getCode(),
                    "统一认证失败 returnCode=" + rc + " returnMsg=" + rm);
        }

        // 4) 复用 login() 的"查机构/角色 + 建 session + 构建响应"逻辑
        ExtUserOrg userOrg = userOrgMapper.selectByUserId(user.getUserId());
        String mainOrgCode = userOrg != null ? userOrg.getOrgCode() : null;
        String mainOrgName = null;
        Integer orgLevel = null;
        String deptNo = null;
        if (mainOrgCode != null) {
            ExtOrgInfo org = orgMapper.selectByOrgCode(mainOrgCode);
            mainOrgName = org != null ? org.getOrgName() : null;
            orgLevel = org != null ? org.getOrgLevel() : null;
            deptNo = org != null ? org.getDeptNo() : null;
        }

        List<PtRole> roles = userRoleMapper.selectRolesByUserId(user.getUserId());
        // 无角色用户禁止登录；并把主角色排到首位作为当前登录角色（前端取 roles[0]）
        PtRole primaryRole = resolvePrimaryRoleAndOrder(user.getUserId(), roles);
        Set<String> roleCodes = roles.stream().map(PtRole::getRoleCode).collect(Collectors.toSet()); // 仅日志用

        // 当前登录角色 = 主角色：会话上下文按「单一当前角色」构建（同 login 链路）
        CurrentUserContext userCtx = buildContextForActiveRole(
                user.getUserId(), user.getUsername(), user.getUserchnname(),
                mainOrgCode, mainOrgName, orgLevel, primaryRole);
        session.setAttribute(SESSION_USER_KEY, userCtx);

        // UIAS 授权信息塞 session 供后续业务消费
        if (uia.getSvcBody() != null && uia.getSvcBody().getUserInfoQryRslt() != null) {
            session.setAttribute("UIAS_USER_BSC_INFO",
                    uia.getSvcBody().getUserInfoQryRslt().getUserBscInfo());
            session.setAttribute("UIAS_INST_INFO_LIST",
                    uia.getSvcBody().getUserInfoQryRslt().getInstInfoList());
        }

        LoginRespDTO resp = new LoginRespDTO();
        resp.setEmpId(user.getUserId());
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getUserchnname());
        resp.setMainOrgCode(mainOrgCode);
        resp.setMainOrgName(mainOrgName);
        resp.setDeptNo(deptNo);
        resp.setToken(session.getId());
        resp.setRoles(roles.stream().map(r -> {
            RoleSimpleDTO dto = new RoleSimpleDTO();
            dto.setRoleId(r.getRoleId());
            dto.setRoleCode(r.getRoleCode());
            dto.setRoleChName(r.getRoleChName());
            dto.setPrimary(r.getRoleId().equals(primaryRole.getRoleId()));
            return dto;
        }).collect(Collectors.toList()));
        resp.setPrimaryRoleId(primaryRole.getRoleId());
        resp.setPrimaryRoleCode(primaryRole.getRoleCode());
        resp.setPrimaryRoleName(primaryRole.getRoleChName());

        log.info("[AuthService.loginByUniAuth] 统一认证登录成功 empId={}, roles={}", user.getUserId(), roleCodes);
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
    /**
     * 登录时校验角色并解析主角色：
     * <ol>
     *   <li>无任何角色的用户禁止登录（抛 AUTH-40107）；</li>
     *   <li>取用户主角色（DEFAULT_ASSIGN=1），缺失时回退第一个角色；</li>
     *   <li>将主角色排到 roles 列表首位，使前端以 roles[0] 作为当前登录角色。</li>
     * </ol>
     *
     * @param userId 用户ID
     * @param roles  用户角色列表（原地排序，主角色置顶）
     * @return 主角色实体
     */
    private PtRole resolvePrimaryRoleAndOrder(String userId, List<PtRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new AuthException(AuthErrorCode.USER_NO_ROLE.getCode(),
                    AuthErrorCode.USER_NO_ROLE.getMessage());
        }
        String primaryRoleId = userRoleMapper.selectPrimaryRoleId(userId);
        final String pid = (primaryRoleId != null) ? primaryRoleId : roles.get(0).getRoleId();
        // 主角色置顶：稳定排序保证主角色在首位，其余角色相对顺序不变
        roles.sort((a, b) -> Boolean.compare(pid.equals(b.getRoleId()), pid.equals(a.getRoleId())));
        return roles.get(0);
    }

    /**
     * 按「单一当前角色」构建会话上下文：roleIds/roleCodes/候选组仅含该角色，
     * activeRoleId 指向该角色，使后续菜单/接口权限/数据范围/工作流候选都按当前角色解析。
     */
    private CurrentUserContext buildContextForActiveRole(
            String empId, String username, String displayName,
            String mainOrgCode, String mainOrgName, Integer orgLevel, PtRole activeRole) {
        Set<String> roleIds = java.util.Set.of(activeRole.getRoleId());
        Set<String> roleCodes = java.util.Set.of(activeRole.getRoleCode());
        // 候选组 Key：ROLE:{当前角色CODE} / USER:{empId} / ORG:{mainOrgCode}
        Set<String> candidateGroupKeys = new java.util.HashSet<>();
        candidateGroupKeys.add("ROLE:" + activeRole.getRoleCode());
        candidateGroupKeys.add("USER:" + empId);
        if (mainOrgCode != null) {
            candidateGroupKeys.add("ORG:" + mainOrgCode);
        }
        boolean isAdmin = "SYS_ADMIN".equals(activeRole.getRoleCode());
        return new CurrentUserContext(empId, username, displayName,
                mainOrgCode, mainOrgName, orgLevel,
                roleIds, roleCodes, candidateGroupKeys, isAdmin, activeRole.getRoleId());
    }

    /**
     * 切换当前会话的激活角色（仅本次会话生效，重新登录回到主角色）。
     * <p>校验目标角色确为该用户已分配角色后，按单角色重建会话上下文，
     * 切换后菜单、接口权限、数据范围、工作流待办均按新角色处理。</p>
     *
     * @param roleId  目标角色ID
     * @param session 当前会话
     * @return 切换后的当前角色信息
     */
    public RoleSimpleDTO switchRole(String roleId, HttpSession session) {
        CurrentUserContext ctx = getCurrentUser(session); // 未登录抛 AUTH-40105
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(ctx.empId());
        PtRole target = roles.stream()
                .filter(r -> r.getRoleId().equals(roleId)).findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.RBAC_DENIED.getCode(),
                        "无法切换到未分配给当前用户的角色"));
        CurrentUserContext newCtx = buildContextForActiveRole(
                ctx.empId(), ctx.username(), ctx.displayName(),
                ctx.mainOrgCode(), ctx.mainOrgName(), ctx.orgLevel(), target);
        session.setAttribute(SESSION_USER_KEY, newCtx);
        log.info("[AuthService.switchRole] empId={} 切换当前角色 -> {}({})",
                ctx.empId(), target.getRoleCode(), roleId);
        RoleSimpleDTO dto = new RoleSimpleDTO();
        dto.setRoleId(target.getRoleId());
        dto.setRoleCode(target.getRoleCode());
        dto.setRoleChName(target.getRoleChName());
        dto.setPrimary(true); // 标记为当前激活角色
        return dto;
    }

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

    /**
     * 当前用户能访问的菜单树（按 PT_USER_ROLE → PT_ROLE_RESOURCE → PT_RESOURCE IS_MENU=1 过滤）。
     * <p>调用方：AuthController.getMyMenus，给前端 AppSidebar 渲染左侧导航。</p>
     * <p>过滤规则：当前用户多角色绑定菜单 ID 的并集 = allowedMenuIds；
     * 拿全量菜单树后做剪枝——节点本身在集合中、或它有后代在集合中的，保留；否则丢弃。
     * 这样确保父分组节点（M_GROUP_PERF 等）只要有子菜单被绑定就可见，菜单层级不至于断链。</p>
     */
    public List<com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO> getMyMenuTree(String empId) {
        log.debug("[AuthService.getMyMenuTree] empId={}", empId);
        if (empId == null || empId.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 按「本次请求生效角色」过滤菜单：会话切换角色后侧边栏只显示当前角色的菜单
        java.util.Set<String> roleIds = cacheService.getEffectiveRoleIds(empId);
        java.util.Set<String> allowed = new java.util.HashSet<>();
        if (roleIds != null) {
            for (String rid : roleIds) {
                java.util.List<String> ids = roleResourceMapper.selectMenuIdsByRoleId(rid);
                if (ids != null) allowed.addAll(ids);
            }
        }
        if (allowed.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO> full = resourceService.getMenuTree();
        return pruneMenuTree(full, allowed);
    }

    private java.util.List<com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO> pruneMenuTree(
            java.util.List<com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO> nodes,
            java.util.Set<String> allowed) {
        java.util.List<com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO> kept = new java.util.ArrayList<>();
        if (nodes == null) return kept;
        for (com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO n : nodes) {
            java.util.List<com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO> prunedKids =
                    pruneMenuTree(n.getChildren(), allowed);
            boolean selfAllowed = allowed.contains(n.getResourceId());
            if (selfAllowed || !prunedKids.isEmpty()) {
                n.setChildren(prunedKids);
                kept.add(n);
            }
        }
        return kept;
    }
}
