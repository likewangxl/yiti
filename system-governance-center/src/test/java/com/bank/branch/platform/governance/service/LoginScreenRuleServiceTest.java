package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.LoginScreenRuleSaveReqDTO;
import com.bank.branch.platform.governance.entity.SysConfigKv;
import com.bank.branch.platform.governance.mapper.ConfigMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginScreenRuleServiceTest {
    @Mock ConfigMapper mapper; @Mock CurrentUserApi current; @Mock ResourceApi resource; @Mock RoleApi roles; @Mock AuditApi audit;
    LoginScreenRuleService service;

    @BeforeEach void setUp() {
        if (TableInfoHelper.getTableInfo(SysConfigKv.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysConfigKv.class);
        }
        service = new LoginScreenRuleService(mapper, new ObjectMapper(), current, resource, roles, audit);
        when(current.getCurrentEmpId()).thenReturn("E1");
        when(resource.matchResource(anyString(), anyString())).thenAnswer(inv -> java.util.Optional.of(resourceDto(inv.getArgument(0), inv.getArgument(1))));
        when(resource.hasResourcePermission(anyString(), anyString())).thenReturn(true);
        RoleRespDTO role = new RoleRespDTO(); role.setRoleId("R1"); when(roles.listEnabledRoles()).thenReturn(List.of(role));
    }

    private static com.bank.branch.platform.auth.api.dto.ResourceDTO resourceDto(String u, String m) {
        var d = new com.bank.branch.platform.auth.api.dto.ResourceDTO(); d.setResourceUrl(u); d.setResourceMethod(m);
        d.setResourceId(u.startsWith("/api/sys") ? "G_LOGIN_SCREEN_SELF" : ("PUT".equals(m) ? "G_LOGIN_SCREEN_PUT" : "G_LOGIN_SCREEN_GET")); return d;
    }
    private LoginScreenRuleSaveReqDTO req(String target) { var r = new LoginScreenRuleSaveReqDTO(); r.setTarget(target); r.setPriority(10); r.setReason("test"); r.setExpectedVersion(""); return r; }
    private SysConfigKv row(String value, String status) { var r = new SysConfigKv(); r.setId("id1"); r.setConfigKey("ui.login-screen.role.R1"); r.setConfigValue(value); r.setStatus(status); r.setValueType("JSON"); return r; }

    @Test void unauthorizedWriteDoesNotTouchStorage() {
        when(resource.hasResourcePermission("E1", "G_LOGIN_SCREEN_PUT")).thenReturn(false);
        assertThrows(BizException.class, () -> service.save("R1", req("PERSONAL")));
        verify(mapper, never()).insert(any(SysConfigKv.class)); verify(mapper, never()).update(any(), any());
    }
    @Test void invalidRoleAndPathAreRejected() {
        RoleRespDTO other = new RoleRespDTO(); other.setRoleId("R2"); when(roles.listEnabledRoles()).thenReturn(List.of(other));
        assertThrows(BizException.class, () -> service.getForRole("R1"));
        assertThrows(BizException.class, () -> service.getForRole("R1/../x"));
    }
    @Test void missingConfigDefaultsToWorkspace() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
        var out = service.getForRole("R1"); assertEquals("WORKSPACE", out.getTarget()); assertEquals("", out.getVersion());
    }
    @Test void readWithoutWriteResourceIsNotEditable() {
        when(resource.matchResource("/api/admin/sys/login-screens/R1", "PUT")).thenReturn(java.util.Optional.empty());
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
        assertFalse(service.getForRole("R1").isEditable());
    }
    @Test void unknownTargetIsRejectedBeforeWrite() {
        assertThrows(BizException.class, () -> service.save("R1", req("UNKNOWN")));
        verify(mapper, never()).insert(any(SysConfigKv.class));
    }
    @Test void disabledConfigCannotBeSilentlyReactivated() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row("{\"target\":\"PERSONAL\",\"priority\":1}", "DISABLED"));
        assertThrows(BizException.class, () -> service.save("R1", req("PERSONAL")));
        verify(mapper, never()).update(any(), any());
    }
    @Test void malformedConfigFailsClosedForCurrentUser() {
        when(current.getCurrentUserContext()).thenReturn(context(Set.of("R1")));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row("not-json", "ACTIVE"));
        assertThrows(BizException.class, () -> service.current());
    }
    @Test void effectiveRoleFilterExcludesOtherRoles() {
        when(current.getCurrentUserContext()).thenReturn(context(Set.of("R1", "R2")));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row("{\"target\":\"PERSONAL\",\"priority\":1}", "ACTIVE"));
        assertEquals(1, service.current().getRules().size()); assertEquals("R1", service.current().getRules().get(0).getRoleId());
    }
    @Test void compareAndSetConflictDoesNotWriteOrAudit() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row("{\"target\":\"PERSONAL\",\"priority\":1}", "ACTIVE"));
        assertThrows(BizException.class, () -> service.save("R1", req("PERSONAL")));
        verify(mapper, never()).update(any(), any()); verify(audit, never()).log(any());
    }
    @Test void successfulCasUpdateUsesVersionFromReadAndAudits() {
        SysConfigKv existing = row("{\"target\":\"PERSONAL\",\"priority\":1}", "ACTIVE");
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(existing);
        String version = service.getForRole("R1").getVersion();
        var request = req("BRANCH"); request.setExpectedVersion(version);
        when(mapper.update(isNull(), any())).thenReturn(1);
        var out = service.save("R1", request);
        assertEquals("BRANCH", out.getTarget());
        assertNotEquals(version, out.getVersion());
        verify(audit).log(any());
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("id") && segment.contains("config_value") && segment.contains("status"), segment);
    }
    @Test void casUpdateZeroRowsIsConflictAndDoesNotAudit() {
        SysConfigKv existing = row("{\"target\":\"PERSONAL\",\"priority\":1}", "ACTIVE");
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(existing);
        var request = req("BRANCH"); request.setExpectedVersion(service.getForRole("R1").getVersion());
        when(mapper.update(isNull(), any())).thenReturn(0);
        assertThrows(BizException.class, () -> service.save("R1", request));
        verify(audit, never()).log(any());
    }
    @Test void serviceRejectsMissingOrOversizedReasonAndVersionWithoutWrite() {
        var missingReason = req("PERSONAL"); missingReason.setReason(null);
        assertThrows(BizException.class, () -> service.save("R1", missingReason));
        var oversized = req("PERSONAL"); oversized.setReason("x".repeat(501));
        assertThrows(BizException.class, () -> service.save("R1", oversized));
        var missingVersion = req("PERSONAL"); missingVersion.setExpectedVersion(null);
        assertThrows(BizException.class, () -> service.save("R1", missingVersion));
        verify(mapper, never()).insert(any(SysConfigKv.class)); verify(mapper, never()).update(any(), any());
    }
    @Test void auditFailureIsPropagated() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(SysConfigKv.class))).thenReturn(1);
        doThrow(new RuntimeException("audit down")).when(audit).log(any());
        assertThrows(RuntimeException.class, () -> service.save("R1", req("PERSONAL")));
        verify(mapper).insert(any(SysConfigKv.class));
    }
    @Test void successfulInsertReturnsVersionAndAudits() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(SysConfigKv.class))).thenReturn(1);
        var out = service.save("R1", req("PERSONAL"));
        assertEquals("PERSONAL", out.getTarget()); assertFalse(out.getVersion().isBlank());
        verify(audit).log(any());
    }
    @Test void insertRaceIsReported() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(SysConfigKv.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("race"));
        assertThrows(BizException.class, () -> service.save("R1", req("PERSONAL")));
    }
    @Test void hugePriorityIsRejected() {
        var r = req("PERSONAL"); r.setPriority(Integer.MAX_VALUE);
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row("{\"target\":\"PERSONAL\",\"priority\":999999999999}", "ACTIVE"));
        assertThrows(BizException.class, () -> service.getForRole("R1"));
    }
    private CurrentUserContext context(Set<String> ids) { return new CurrentUserContext("E1", "e1", "n", "O1", "Org", 1, ids, Set.of("R1"), Set.of(), false, null); }
}
