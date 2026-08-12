package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtOrgGroup;
import com.bank.branch.platform.auth.entity.PtOrgProfile;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.common.web.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 机构画像与命名机构组高危配置的结构化审计服务。
 *
 * <p>认证模块不直接依赖治理模块，而是仅依赖 common-aop 的审计处理器契约。写操作在
 * {@link OrgGroupService} 的事务内同步调用本服务：处理器不是实际持久化实现或写入失败时，
 * 抛出业务异常使配置事务回滚，避免权限范围变更缺少可追溯审计。</p>
 */
@Service
@RequiredArgsConstructor
public class OrgGroupAuditService {

    private static final String BIZ_TYPE_SYS_CONFIG = "SYS_CONFIG";
    private static final String PROFILE_TARGET = "PT_ORG_PROFILE";
    private static final String GROUP_TARGET = "PT_ORG_GROUP";
    private static final String MEMBER_TARGET = "PT_ORG_GROUP_MEMBER";
    private static final String ROLE_TARGET = "PT_ROLE_ORG_GROUP";

    private final AuditLogHandler auditLogHandler;
    private final ObjectMapper objectMapper;

    /** 记录机构画像新增或修改前后的结构化快照。 */
    public void profileChanged(PtOrgProfile before, PtOrgProfile after, String reason) {
        String orgCode = after != null ? after.getOrgCode() : before == null ? null : before.getOrgCode();
        persist("ORG_PROFILE_CHANGE", PROFILE_TARGET, orgCode,
                "/api/admin/org-profiles/" + orgCode, "PUT",
                snapshot(before == null ? null : profileSnapshot(before)),
                snapshot(after == null ? null : profileSnapshot(after)),
                null, null, reason);
    }

    /** 记录命名机构组创建后的结构化快照。 */
    public void groupCreated(PtOrgGroup after, String reason) {
        String groupCode = after == null ? null : after.getGroupCode();
        persist("ORG_GROUP_CREATE", GROUP_TARGET, groupCode,
                "/api/admin/org-groups", "POST",
                null, snapshot(after == null ? null : groupSnapshot(after)),
                null, null, reason);
    }

    /** 记录命名机构组基本信息变更的前后快照。 */
    public void groupChanged(PtOrgGroup before, PtOrgGroup after, String reason) {
        String groupCode = after != null ? after.getGroupCode() : before == null ? null : before.getGroupCode();
        persist("ORG_GROUP_CHANGE", GROUP_TARGET, groupCode,
                "/api/admin/org-groups/" + groupCode, "PUT",
                snapshot(before == null ? null : groupSnapshot(before)),
                snapshot(after == null ? null : groupSnapshot(after)),
                null, null, reason);
    }

    /** 记录机构组成员覆盖替换的快照及集合差异。 */
    public void membersReplaced(String groupCode, Set<String> before, Set<String> after, String reason) {
        persist("ORG_GROUP_MEMBER_CHANGE", MEMBER_TARGET, groupCode,
                "/api/admin/org-groups/" + groupCode + "/members", "PUT",
                snapshot(new MemberSnapshot(groupCode, sorted(before))),
                snapshot(new MemberSnapshot(groupCode, sorted(after))),
                snapshot(sortedDifference(after, before)), snapshot(sortedDifference(before, after)), reason);
    }

    /** 记录角色-机构组覆盖替换的快照及集合差异。 */
    public void rolesReplaced(String groupCode, Set<String> before, Set<String> after, String reason) {
        persist("ORG_GROUP_ROLE_CHANGE", ROLE_TARGET, groupCode,
                "/api/admin/org-groups/" + groupCode + "/roles", "PUT",
                snapshot(new RoleBindingSnapshot(groupCode, sorted(before))),
                snapshot(new RoleBindingSnapshot(groupCode, sorted(after))),
                snapshot(sortedDifference(after, before)), snapshot(sortedDifference(before, after)), reason);
    }

    private void persist(String action, String targetType, String targetId, String url, String method,
                         String before, String after, String addedItems, String removedItems, String reason) {
        if (!auditLogHandler.isPersistent()) {
            throw auditFailed(null);
        }
        DataScopeContext context = DataScopeContext.current();
        String operator = context != null && context.getEmpId() != null && !context.getEmpId().isBlank()
                ? context.getEmpId() : "system";
        String operatorOrg = context == null ? null : context.getOrgCode();
        try {
            auditLogHandler.handle(new AuditLogEvent(
                    action, targetType, targetId, targetId,
                    operator, operatorOrg, Instant.now(),
                    null, null, before, after, reason,
                    BIZ_TYPE_SYS_CONFIG, url, method, null,
                    0L, traceId(), 200, null,
                    targetType, targetId, addedItems, removedItems));
        } catch (RuntimeException ex) {
            throw auditFailed(ex);
        }
    }

    private String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw auditFailed(ex);
        }
    }

    private static List<String> sorted(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static List<String> sortedDifference(Collection<String> left, Collection<String> right) {
        Set<String> rightValues = new HashSet<>(sorted(right));
        return sorted(left).stream().filter(value -> !rightValues.contains(value)).toList();
    }

    private static String traceId() {
        String traceId = MdcUtils.getTraceId();
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    private static BizException auditFailed(Throwable cause) {
        return cause == null
                ? new BizException(AuthErrorCode.ORG_CONFIG_AUDIT_FAILED.getCode(),
                AuthErrorCode.ORG_CONFIG_AUDIT_FAILED.getMessage())
                : new BizException(AuthErrorCode.ORG_CONFIG_AUDIT_FAILED.getCode(),
                AuthErrorCode.ORG_CONFIG_AUDIT_FAILED.getMessage(), cause);
    }

    private static ProfileSnapshot profileSnapshot(PtOrgProfile profile) {
        return new ProfileSnapshot(profile.getOrgCode(), profile.getOrgNature(), profile.getOperatingLevel(),
                profile.getOwnerOperatingOrgCode(), profile.getCityCode(), profile.getCityName(),
                profile.getLng(), profile.getLat(), profile.getCoordSys(), profile.getStatus(),
                profile.getVersion(), profile.getRemark());
    }

    private static GroupSnapshot groupSnapshot(PtOrgGroup group) {
        return new GroupSnapshot(group.getGroupCode(), group.getGroupName(), group.getGroupPurpose(),
                group.getStatus(), group.getVersion(), group.getRemark());
    }

    private record ProfileSnapshot(String orgCode, String orgNature, String operatingLevel,
                                   String ownerOperatingOrgCode, String cityCode, String cityName,
                                   BigDecimal lng, BigDecimal lat, String coordSys, String status,
                                   Integer version, String remark) {
    }

    private record GroupSnapshot(String groupCode, String groupName, String groupPurpose,
                                 String status, Integer version, String remark) {
    }

    private record MemberSnapshot(String groupCode, List<String> orgCodes) {
    }

    private record RoleBindingSnapshot(String groupCode, List<String> roleCodes) {
    }
}
