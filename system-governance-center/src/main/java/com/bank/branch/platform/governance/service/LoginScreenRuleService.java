package com.bank.branch.platform.governance.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.*;
import com.bank.branch.platform.governance.entity.SysConfigKv;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.ConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/** 登录后首屏偏好服务；所有实际大屏访问仍由大屏自身授权链路决定。 */
@Service
@RequiredArgsConstructor
public class LoginScreenRuleService {
    private static final String PREFIX = "ui.login-screen.role.";
    private static final String READ_RESOURCE = "G_LOGIN_SCREEN_GET";
    private static final String WRITE_RESOURCE = "G_LOGIN_SCREEN_PUT";
    private static final String SELF_RESOURCE = "G_LOGIN_SCREEN_SELF";
    private static final Set<String> TARGETS = Set.of("WORKSPACE", "PERSONAL", "BRANCH", "PROVINCE", "RETAIL", "CORPORATE");
    private final ConfigMapper configMapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserApi currentUserApi;
    private final ResourceApi resourceApi;
    private final RoleApi roleApi;
    private final AuditApi auditApi;

    /** 查询角色的登录首屏规则；无配置时返回工作台默认值。 */
    public LoginScreenRuleDTO getForRole(String roleId) {
        validateRoleId(roleId);
        requirePermission(READ_RESOURCE, "/api/admin/sys/login-screens/" + roleId, "GET");
        requireRole(roleId);
        SysConfigKv row = findRule(roleId);
        LoginScreenRuleDTO result = row == null ? defaultRule(roleId) : parse(row);
        result.setEditable(canEdit(roleId));
        return result;
    }

    /** 保存角色登录首屏规则；权限、角色、版本和审计均在服务层校验。 */
    @Transactional
    public LoginScreenRuleDTO save(String roleId, LoginScreenRuleSaveReqDTO req) {
        validateRoleId(roleId);
        requirePermission(WRITE_RESOURCE, "/api/admin/sys/login-screens/" + roleId, "PUT");
        requireRole(roleId);
        if (req == null || req.getReason() == null || req.getReason().isBlank() || req.getReason().length() > 500
                || req.getPriority() == null || req.getExpectedVersion() == null) {
            throw invalid("request");
        }
        String target = normalizeTarget(req.getTarget());
        if (req.getPriority() < 0 || req.getPriority() > 10000) throw invalid("priority");
        SysConfigKv before = findRule(roleId);
        if (before != null && !"ACTIVE".equalsIgnoreCase(before.getStatus())) {
            throw new BizException("GOV-40910", "登录首屏规则已停用，请先恢复配置");
        }
        String expected = Optional.ofNullable(req.getExpectedVersion()).orElse("");
        String actual = before == null ? "" : version(before.getConfigValue());
        if (!Objects.equals(expected, actual)) throw new BizException("GOV-40910", "登录首屏规则已被其他人修改");
        String raw;
        try { raw = objectMapper.writeValueAsString(Map.of("target", target, "priority", req.getPriority())); }
        catch (Exception e) { throw new BizException(GovErrorCode.CONFIG_VALUE_TYPE_INVALID.getCode(), "登录首屏规则序列化失败", e); }
        SysConfigKv row = new SysConfigKv();
        row.setConfigKey(key(roleId)); row.setConfigValue(raw); row.setValueType("JSON"); row.setStatus("ACTIVE");
        row.setRemark("登录首屏规则");
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setCreatedBy(currentUserApi.getCurrentEmpId());
        row.setCreatedTime(LocalDateTime.now());
        if (before == null) {
            try {
                if (configMapper.insert(row) != 1) throw new BizException("GOV-50013", "登录首屏规则保存失败");
            } catch (DuplicateKeyException e) { throw new BizException("GOV-40910", "登录首屏规则已被其他人修改", e); }
        } else {
            int count = configMapper.update(null, new LambdaUpdateWrapper<SysConfigKv>()
                    .eq(SysConfigKv::getId, before.getId()).eq(SysConfigKv::getConfigValue, before.getConfigValue())
                    .eq(SysConfigKv::getStatus, "ACTIVE")
                    .set(SysConfigKv::getConfigValue, raw).set(SysConfigKv::getValueType, "JSON")
                    .set(SysConfigKv::getStatus, "ACTIVE").set(SysConfigKv::getUpdatedBy, currentUserApi.getCurrentEmpId())
                    .set(SysConfigKv::getUpdatedTime, LocalDateTime.now()));
            if (count != 1) throw new BizException("GOV-40910", "登录首屏规则已被其他人修改");
        }
        auditApi.log(AuditLogCmd.builder().empId(currentUserApi.getCurrentEmpId()).bizType("SYS_CONFIG").bizAction("CONFIG")
                .resourceUrl("/api/admin/sys/login-screens/" + roleId).requestMethod("PUT").reason(req.getReason())
                .targetType("SYS_CONFIG_KV").targetId(key(roleId)).beforeSnapshot(before == null ? null : before.getConfigValue())
                .afterSnapshot(raw).build());
        LoginScreenRuleDTO result = parse(row);
        result.setVersion(version(raw));
        result.setEditable(true);
        return result;
    }

    /** 返回当前登录用户有效角色中配置的首屏规则；任何坏配置均 fail-close。 */
    public LoginScreenRulesRespDTO current() {
        requirePermission(SELF_RESOURCE, "/api/sys/login-screens", "GET");
        CurrentUserContext ctx = currentUserApi.getCurrentUserContext();
        Set<String> ids = Optional.ofNullable(ctx.roleIds()).orElse(Set.of());
        Set<String> enabled = roleApi.listEnabledRoles().stream().map(RoleRespDTO::getRoleId).collect(Collectors.toSet());
        List<LoginScreenRuleDTO> rules = new ArrayList<>();
        for (String id : ids) {
            if (!enabled.contains(id)) continue;
            SysConfigKv row = findRule(id);
            if (row == null || !"ACTIVE".equalsIgnoreCase(row.getStatus())) continue;
            LoginScreenRuleDTO rule = parse(row);
            rule.setEditable(false);
            rules.add(rule);
        }
        return new LoginScreenRulesRespDTO(rules);
    }

    private SysConfigKv findRule(String roleId) {
        return configMapper.selectOne(new LambdaQueryWrapper<SysConfigKv>()
                .eq(SysConfigKv::getConfigKey, key(roleId)));
    }

    private LoginScreenRuleDTO parse(SysConfigKv row) {
        try {
            if (!"JSON".equalsIgnoreCase(row.getValueType())) throw new IllegalArgumentException();
            JsonNode n = objectMapper.readTree(row.getConfigValue());
            if (!n.isObject() || !n.has("target") || !n.get("target").isTextual()
                    || !n.has("priority") || !n.get("priority").isIntegralNumber()
                    || !n.get("priority").canConvertToInt()) throw new IllegalArgumentException();
            String target = normalizeTarget(n.get("target").textValue()); int priority = n.get("priority").intValue();
            if (!"ACTIVE".equalsIgnoreCase(row.getStatus()) || priority < 0 || priority > 10000) throw new IllegalArgumentException();
            LoginScreenRuleDTO dto = new LoginScreenRuleDTO(); dto.setRoleId(row.getConfigKey().substring(PREFIX.length())); dto.setTarget(target); dto.setPriority(priority); dto.setVersion(version(row.getConfigValue())); return dto;
        } catch (Exception e) { throw new BizException(GovErrorCode.CONFIG_VALUE_TYPE_INVALID.getCode(), "登录首屏规则无效", e); }
    }
    private LoginScreenRuleDTO defaultRule(String id) { LoginScreenRuleDTO d = new LoginScreenRuleDTO(); d.setRoleId(id); d.setTarget("WORKSPACE"); d.setPriority(100); d.setVersion(""); return d; }
    private String key(String id) { if (id == null || id.isBlank() || id.contains(".") || id.contains("/") || id.contains("\\")) throw invalid("roleId"); return PREFIX + id; }
    private void requireRole(String id) { if (roleApi.listEnabledRoles().stream().noneMatch(r -> id.equals(r.getRoleId()))) throw new BizException("GOV-40410", "角色不存在或已停用"); }
    private void validateRoleId(String id) { if (id == null || !id.matches("[A-Za-z0-9_-]{1,50}")) throw invalid("roleId"); }
    private void requirePermission(String resource, String url, String method) {
        if (resourceApi.matchResource(url, method).map(r -> resource.equals(r.getResourceId())).orElse(false) == false) {
            throw new BizException("GOV-40311", "登录首屏资源未登记");
        }
        String emp = currentUserApi.getCurrentEmpId();
        if (!resourceApi.hasResourcePermission(emp, resource)) throw new BizException("GOV-40310", "无登录首屏配置权限");
    }
    private boolean canEdit(String roleId) {
        return resourceApi.matchResource("/api/admin/sys/login-screens/" + roleId, "PUT")
                .map(r -> WRITE_RESOURCE.equals(r.getResourceId())).orElse(false)
                && resourceApi.hasResourcePermission(currentUserApi.getCurrentEmpId(), WRITE_RESOURCE);
    }
    private String normalizeTarget(String t) { String v = t == null ? "" : t.trim().toUpperCase(Locale.ROOT); if (!TARGETS.contains(v)) throw invalid("target"); return v; }
    private BizException invalid(String field) { return new BizException("GOV-42210", "登录首屏参数无效: " + field); }
    private String version(String raw) { try { MessageDigest d = MessageDigest.getInstance("SHA-256"); byte[] b = d.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8)); StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
}
