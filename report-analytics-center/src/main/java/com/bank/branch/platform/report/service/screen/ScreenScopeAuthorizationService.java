package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.auth.api.dto.OrgGroupRoleCheckDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenAccessRole;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenAccessRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大屏四项门禁与命名机构组授权适配器。
 *
 * <p>这里集中实现“资源允许、屏级角色白名单、机构组有效、同一角色同时命中”这一
 * 相关性约束，避免保存、发布和运行时各自实现一套容易漂移的权限拼接逻辑。资源级
 * PT_RESOURCE/@BizAuth 仍由 auth 拦截器负责，本类只处理屏自身的配置门禁。</p>
 */
@Slf4j
@Component
public class ScreenScopeAuthorizationService {

    public static final String LEGACY_CONTEXT = "LEGACY_CONTEXT";
    public static final String NAMED_GROUP = "NAMED_GROUP";
    public static final String ACTIVE = "ACTIVE";

    private final CurrentUserApi currentUserApi;
    private final OrgGroupApi orgGroupApi;
    private final RptScreenAccessRoleMapper accessRoleMapper;

    public ScreenScopeAuthorizationService(CurrentUserApi currentUserApi,
                                            OrgGroupApi orgGroupApi,
                                            RptScreenAccessRoleMapper accessRoleMapper,
                                            AuditApi ignoredAuditApi) {
        this.currentUserApi = currentUserApi;
        this.orgGroupApi = orgGroupApi;
        this.accessRoleMapper = accessRoleMapper;
    }

    /** 屏级角色白名单（仅 ACTIVE）。 */
    public Set<String> allowedRoleCodes(Long screenId) {
        return accessRoleMapper.selectList(new LambdaQueryWrapper<RptScreenAccessRole>()
                        .eq(RptScreenAccessRole::getScreenId, screenId)
                        .eq(RptScreenAccessRole::getStatus, ACTIVE))
                .stream()
                .map(RptScreenAccessRole::getRoleCode)
                .filter(this::nonBlank)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 保存阶段校验屏的机构范围配置。
     *
     * <p>草稿保存允许先缺坐标，但机构组本身不能缺失、停用或为空；角色白名单若已提交，
     * 则立即校验角色有效性。发布阶段通过 {@code requireRoles=true} 再收紧为至少一个可满足角色。</p>
     */
    public void validateForSave(RptScreen screen, Collection<String> roleCodes, boolean requireRoles) {
        if (!NAMED_GROUP.equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
            return;
        }
        String groupCode = trimToNull(screen.getOrgGroupCode());
        if (groupCode == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        GroupSnapshot group = requireGroup(groupCode);
        Set<String> normalizedRoles = normalizeRoleCodes(roleCodes);
        if (requireRoles && normalizedRoles.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_ACCESS_ROLE_REQUIRED);
        }
        if (!normalizedRoles.isEmpty()) {
            OrgGroupRoleCheckDTO check = checkRoleBindings(groupCode, normalizedRoles);
            if (hasInvalidOrUnbound(check) || !check.isSatisfiable()) {
                throw new RptException(RptErrorCode.SCREEN_ACCESS_ROLE_REQUIRED);
            }
        }
        // 触发 group.memberCodes 的非空 fail-close 检查，避免仅凭 group 元数据放行空组。
        if (group.memberCodes().isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
    }

    /**
     * 运行时解析授权机构集合。返回空集合代表拒绝，调用方必须 fail-close。
     */
    public Set<String> authorize(RptScreen screen) {
        String scopeMode = normalizeScopeMode(screen.getOrgScopeMode());
        Set<String> allowed;
        Set<String> current;
        try {
            allowed = allowedRoleCodes(screen.getId());
            current = safeRoles();
        } catch (RptException e) {
            throw e;
        } catch (RuntimeException e) {
            // 角色白名单或当前用户角色读取失败时不能把异常当成“未配置”放行。
            log.warn("[ScreenScopeAuthorization] 读取屏级/当前角色失败，fail-close cause={}",
                    e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED, e);
        }
        if (!allowed.isEmpty()) {
            if (current.stream().noneMatch(allowed::contains)) {
                deny("屏级角色白名单无交集");
            }
        } else if (NAMED_GROUP.equals(scopeMode)) {
            deny("命名机构组屏未配置角色白名单");
        }
        if (!NAMED_GROUP.equals(scopeMode)) {
            return Set.of();
        }

        String groupCode = trimToNull(screen.getOrgGroupCode());
        if (groupCode == null) {
            deny("命名机构组编码缺失");
        }
        GroupSnapshot group = requireGroup(groupCode);
        try {
            OrgGroupScopeDTO result = orgGroupApi.resolveAuthorizedScope(
                    currentUserApi.getCurrentEmpId(), groupCode, allowed);
            if (result == null || !result.isAuthorized()
                    || result.getMemberOrgCodes() == null || result.getMemberOrgCodes().isEmpty()) {
                deny("当前用户未通过同一角色机构组门禁");
            }
            // auth API 的 authorized 结果必须同时携带实际命中的角色；这里再次收口
            // current ∩ whitelist ∩ matched，避免错误实现或陈旧实现把两个不同角色拼成一项权限。
            Set<String> matched = normalizeRoleCodes(result.getMatchedRoleCodes());
            Set<String> sameRole = new LinkedHashSet<>(current);
            sameRole.retainAll(allowed);
            sameRole.retainAll(matched);
            if (sameRole.isEmpty()) {
                deny("当前用户未通过同一角色机构组门禁");
            }
            Set<String> authorizedMembers = normalizeRoles(result.getMemberOrgCodes());
            authorizedMembers.retainAll(group.memberCodes());
            if (authorizedMembers.isEmpty()) {
                // 跨模块返回的机构集合也必须被屏配置组钳制，不能因 API 数据异常扩大范围。
                deny("机构组授权集合为空");
            }
            return authorizedMembers;
        } catch (RptException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[ScreenScopeAuthorization] OrgGroupApi 异常，fail-close groupCode={} cause={}",
                    groupCode, e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED, e);
        }
    }

    /** 发布阶段校验屏级白名单与机构组绑定可满足，并检查画像可被读取。 */
    public void validatePublishRoles(RptScreen screen) {
        if (!NAMED_GROUP.equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
            return;
        }
        Set<String> roles = allowedRoleCodes(screen.getId());
        validateForSave(screen, roles, true);
        GroupSnapshot group = requireGroup(screen.getOrgGroupCode());
        Map<String, OrgProfileDTO> profiles;
        try {
            profiles = orgGroupApi.getActiveProfiles(group.memberCodes());
        } catch (RptException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID, e);
        }
        if (profiles == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        // Publishing is a promise that every configured member can be represented at runtime.
        // Extra response keys are ignored, but a missing/inactive/mismatched configured member
        // must fail closed instead of making the screen publish successfully and fail on read.
        for (String memberCode : group.memberCodes()) {
            OrgProfileDTO profile = profiles.get(memberCode);
            if (profile == null || !ACTIVE.equalsIgnoreCase(profile.getStatus())
                    || profile.getOrgCode() == null
                    || !memberCode.equals(profile.getOrgCode().trim())) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
        }
    }

    /** 获取当前屏绑定机构组的有效画像，供复合地图渲染器使用。 */
    public Map<String, OrgProfileDTO> activeProfiles(RptScreen screen, Set<String> memberCodes) {
        if (!NAMED_GROUP.equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
            return Map.of();
        }
        try {
            Map<String, OrgProfileDTO> profiles = orgGroupApi.getActiveProfiles(memberCodes);
            if (profiles == null) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            return profiles;
        } catch (RuntimeException e) {
            log.warn("[ScreenScopeAuthorization] 读取机构画像失败，fail-close cause={}", e.getMessage());
            if (e instanceof RptException rptException) {
                throw rptException;
            }
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID, e);
        }
    }

    /** 发布校验读取屏绑定机构组的直接成员，不叠加当前员工权限。 */
    public Set<String> configuredMemberCodes(RptScreen screen) {
        if (!NAMED_GROUP.equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
            return Set.of();
        }
        String groupCode = trimToNull(screen.getOrgGroupCode());
        if (groupCode == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return requireGroup(groupCode).memberCodes();
    }

    /**
     * NAMED_GROUP 配置态试跑使用的显式机构组校验入口。
     * 它只返回服务端实际有效成员，调用方不得回退到请求中的 orgCode。
     */
    public Set<String> testGroupMemberCodes(String groupCode) {
        String normalized = trimToNull(groupCode);
        if (normalized == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return requireGroup(normalized).memberCodes();
    }

    /** 统一机构组快照校验，避免把停用组或空组当成全量。 */
    private GroupSnapshot requireGroup(String groupCode) {
        try {
            OrgGroupDTO group = orgGroupApi.getGroup(groupCode);
            Set<String> members = normalizeRoles(orgGroupApi.listActiveMemberCodes(groupCode));
            if (group == null || !ACTIVE.equalsIgnoreCase(group.getStatus()) || members.isEmpty()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            return new GroupSnapshot(group, members);
        } catch (RptException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[ScreenScopeAuthorization] 读取机构组失败，fail-close groupCode={} cause={}",
                    groupCode, e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID, e);
        }
    }

    private OrgGroupRoleCheckDTO checkRoleBindings(String groupCode, Set<String> roleCodes) {
        try {
            OrgGroupRoleCheckDTO check = orgGroupApi.checkRoleBindings(groupCode, roleCodes);
            if (check == null) {
                throw new RptException(RptErrorCode.SCREEN_ACCESS_ROLE_REQUIRED);
            }
            return check;
        } catch (RptException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RptException(RptErrorCode.SCREEN_ACCESS_ROLE_REQUIRED, e);
        }
    }

    private Set<String> safeRoles() {
        Set<String> roles = currentUserApi.getCurrentRoleCodes();
        return roles == null ? Set.of() : normalizeRoleCodes(roles);
    }

    private void deny(String reason) {
        log.warn("[ScreenScopeAuthorization] 大屏访问拒绝 empId={} reason={}",
                currentUserApi.getCurrentEmpId(), reason);
        throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
    }

    private boolean hasInvalidOrUnbound(OrgGroupRoleCheckDTO check) {
        return (check.getInvalidRoleCodes() != null && !check.getInvalidRoleCodes().isEmpty())
                || (check.getUnboundRoleCodes() != null && !check.getUnboundRoleCodes().isEmpty());
    }

    private String normalizeScopeMode(String value) {
        return value == null || value.isBlank() ? LEGACY_CONTEXT : value.trim().toUpperCase();
    }

    private Set<String> normalizeRoles(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(this::nonBlank).map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizeRoleCodes(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(this::nonBlank)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record GroupSnapshot(OrgGroupDTO group, Set<String> memberCodes) {
    }
}
