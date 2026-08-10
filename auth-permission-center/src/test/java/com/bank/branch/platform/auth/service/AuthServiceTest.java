package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.service.BizScopeService;
import com.bank.branch.platform.auth.service.PermissionCacheService;
import com.bank.branch.platform.auth.uniauth.UniAuthSidecarClient;
import com.bank.branch.platform.auth.uniauth.dto.UniAuthRespDTO;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import org.mockito.ArgumentCaptor;
import com.bank.branch.platform.common.web.exception.AuthException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Mock UserOrgMapper userOrgMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock OrgMapper orgMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock HttpSession session;
    @Mock PermissionCacheService cacheService;
    @Mock BizScopeService bizScopeService;
    @Mock ResourceMapper resourceMapper;
    @Mock UniAuthSidecarClient uniAuthSidecarClient;
    @InjectMocks AuthService authService;

    @BeforeEach
    void injectOptionalUniAuthClient() {
        ReflectionTestUtils.setField(authService, "uniAuthSidecarClient", uniAuthSidecarClient);
    }

    @Test
    void login_shouldThrowAuthExceptionWhenUserNotFound() {
        when(userMapper.selectByUsername("unknown")).thenReturn(null);

        assertThatThrownBy(() -> authService.login("unknown", "pw", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40101"));
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin", "wrong", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40101"));
        verify(userMapper).updatePassWrongCount(eq("E001"), eq(1));
    }

    @Test
    void login_shouldThrowWhenAccountLocked() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        user.setIsLocked(1);
        when(userMapper.selectByUsername("admin")).thenReturn(user);

        assertThatThrownBy(() -> authService.login("admin", "pw", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40102"));
    }

    @Test
    void login_shouldThrowWhenPasswordAttemptsExceeded() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        user.setPassWrongCount(5); // already at limit
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin", "pw", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40106"));
    }

    @Test
    void login_shouldSucceedAndResetWrongCount() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        user.setPassWrongCount(2);
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        ExtUserOrg userOrg = new ExtUserOrg();
        userOrg.setUserId("E001");
        userOrg.setOrgCode("ORG001");
        when(userOrgMapper.selectByUserId("E001")).thenReturn(userOrg);

        com.bank.branch.platform.auth.entity.ExtOrgInfo org = new com.bank.branch.platform.auth.entity.ExtOrgInfo();
        org.setOrgCode("ORG001");
        org.setOrgName("测试分行");
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(org);

        PtRole role = new PtRole();
        role.setRoleId("R_RM");
        role.setRoleCode("CUST_MANAGER");
        role.setRoleChName("客户经理");
        // 用可变 List：login 会对角色列表排序把主角色置顶（List.of 不可变会抛 UnsupportedOperationException）
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(new java.util.ArrayList<>(List.of(role)));

        LoginRespDTO resp = authService.login("admin", "pass", session);

        assertThat(resp.getEmpId()).isEqualTo("E001");
        assertThat(resp.getMainOrgCode()).isEqualTo("ORG001");
        assertThat(resp.getRoles()).hasSize(1);
        verify(userMapper).updatePassWrongCount("E001", 0); // reset on success

        // candidateGroupKeys 应覆盖 USER/ROLE/ORG 三种 Flowable 候选类型，
        // 否则 USER:E001 / ORG:ORG001 类型的任务候选无法匹配（V1.x bug fix 2026-05-20）
        ArgumentCaptor<CurrentUserContext> ctxCap = ArgumentCaptor.forClass(CurrentUserContext.class);
        verify(session).setAttribute(eq("currentUser"), ctxCap.capture());
        assertThat(ctxCap.getValue().candidateGroupKeys())
                .contains("ROLE:CUST_MANAGER", "USER:E001", "ORG:ORG001");
    }

    @Test
    void login_shouldBuildUnionContextFromAllValidRoles() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        ExtUserOrg userOrg = new ExtUserOrg();
        userOrg.setUserId("E001");
        userOrg.setOrgCode("ORG001");
        when(userOrgMapper.selectByUserId("E001")).thenReturn(userOrg);

        com.bank.branch.platform.auth.entity.ExtOrgInfo org = new com.bank.branch.platform.auth.entity.ExtOrgInfo();
        org.setOrgCode("ORG001");
        org.setOrgName("测试分行");
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(org);

        PtRole reporter = makeRole("R_REPORTER", "PARTY_REPORTER", "党建报送员");
        PtRole admin = makeRole("R_ADMIN", "SYS_ADMIN", "系统管理员");
        when(userRoleMapper.selectRolesByUserId("E001"))
                .thenReturn(new java.util.ArrayList<>(List.of(reporter, admin)));
        when(userRoleMapper.selectPrimaryRoleId("E001")).thenReturn("R_REPORTER");

        authService.login("admin", "pass", session);

        ArgumentCaptor<CurrentUserContext> ctxCap = ArgumentCaptor.forClass(CurrentUserContext.class);
        verify(session).setAttribute(eq(AuthService.SESSION_USER_KEY), ctxCap.capture());
        CurrentUserContext ctx = ctxCap.getValue();
        assertThat(ctx.roleIds()).containsExactlyInAnyOrder("R_REPORTER", "R_ADMIN");
        assertThat(ctx.roleCodes()).containsExactlyInAnyOrder("PARTY_REPORTER", "SYS_ADMIN");
        assertThat(ctx.candidateGroupKeys()).containsExactlyInAnyOrder(
                "ROLE:PARTY_REPORTER", "ROLE:SYS_ADMIN", "USER:E001", "ORG:ORG001");
        assertThat(ctx.systemAdmin()).isTrue();
        assertThat(ctx.activeRoleId()).isNull();
    }

    @Test
    void loginByUniAuth_shouldBuildUnionContextFromAllValidRoles() {
        PtUser user = makeEnabledUser("E001", "E001", "unused");
        when(userMapper.selectByUsername("E001")).thenReturn(user);

        UniAuthRespDTO uniAuthResp = new UniAuthRespDTO();
        UniAuthRespDTO.RspSvcHeader header = new UniAuthRespDTO.RspSvcHeader();
        header.setReturnCode("000000000000");
        uniAuthResp.setRspSvcHeader(header);
        when(uniAuthSidecarClient.queryUserInfo("E001")).thenReturn(uniAuthResp);

        PtRole reporter = makeRole("R_REPORTER", "PARTY_REPORTER", "党建报送员");
        PtRole reviewer = makeRole("R_REVIEWER", "PARTY_REVIEWER", "党建审核员");
        when(userRoleMapper.selectRolesByUserId("E001"))
                .thenReturn(new java.util.ArrayList<>(List.of(reporter, reviewer)));
        when(userRoleMapper.selectPrimaryRoleId("E001")).thenReturn("R_REPORTER");

        authService.loginByUniAuth("E001", session);

        ArgumentCaptor<CurrentUserContext> ctxCap = ArgumentCaptor.forClass(CurrentUserContext.class);
        verify(session).setAttribute(eq(AuthService.SESSION_USER_KEY), ctxCap.capture());
        CurrentUserContext ctx = ctxCap.getValue();
        assertThat(ctx.roleIds()).containsExactlyInAnyOrder("R_REPORTER", "R_REVIEWER");
        assertThat(ctx.roleCodes()).containsExactlyInAnyOrder("PARTY_REPORTER", "PARTY_REVIEWER");
        assertThat(ctx.candidateGroupKeys()).containsExactlyInAnyOrder(
                "ROLE:PARTY_REPORTER", "ROLE:PARTY_REVIEWER", "USER:E001");
        assertThat(ctx.systemAdmin()).isFalse();
        assertThat(ctx.activeRoleId()).isNull();
    }

    @Test
    void switchRole_shouldValidateAssignmentWithoutChangingUnionSessionContext() {
        CurrentUserContext existing = new CurrentUserContext(
                "E001", "E001", "测试用户", "ORG001", "测试分行", 2,
                Set.of("R_REPORTER", "R_REVIEWER"),
                Set.of("PARTY_REPORTER", "PARTY_REVIEWER"),
                Set.of("ROLE:PARTY_REPORTER", "ROLE:PARTY_REVIEWER", "USER:E001", "ORG:ORG001"),
                false, null);
        when(session.getAttribute(AuthService.SESSION_USER_KEY)).thenReturn(existing);
        PtRole reporter = makeRole("R_REPORTER", "PARTY_REPORTER", "党建报送员");
        PtRole reviewer = makeRole("R_REVIEWER", "PARTY_REVIEWER", "党建审核员");
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(List.of(reporter, reviewer));
        when(userRoleMapper.selectPrimaryRoleId("E001")).thenReturn("R_REPORTER");

        RoleSimpleDTO result = authService.switchRole("R_REVIEWER", session);

        assertThat(result.getRoleId()).isEqualTo("R_REVIEWER");
        assertThat(result.getPrimary()).isFalse();
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    void logout_shouldInvalidateSession() {
        authService.logout(session);
        verify(session).invalidate();
    }

    private PtUser makeEnabledUser(String userId, String username, String pwd) {
        PtUser u = new PtUser();
        u.setUserId(userId);
        u.setUsername(username);
        u.setUserchnname("测试用户");
        u.setPwd(pwd);
        u.setIsEnabled(0);  // 0=启用
        u.setIsLocked(0);
        u.setIsExpired(0);
        u.setPassWrongCount(0);
        return u;
    }

    private PtRole makeRole(String roleId, String roleCode, String roleName) {
        PtRole role = new PtRole();
        role.setRoleId(roleId);
        role.setRoleCode(roleCode);
        role.setRoleChName(roleName);
        return role;
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    @Test
    void login_shouldThrowWhenAccountDisabled() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        user.setIsEnabled(1); // 1=未启用
        when(userMapper.selectByUsername("admin")).thenReturn(user);

        assertThatThrownBy(() -> authService.login("admin", "pw", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40104"));
    }

    @Test
    void login_shouldThrowWhenAccountExpired() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        user.setIsExpired(1);
        when(userMapper.selectByUsername("admin")).thenReturn(user);

        assertThatThrownBy(() -> authService.login("admin", "pw", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40103"));
    }

    @Test
    void login_shouldHandleNullUserOrg() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(userOrgMapper.selectByUserId("E001")).thenReturn(null);
        // 无角色用户现禁止登录，这里给一个角色以测试「主机构为空」场景
        PtRole role = new PtRole();
        role.setRoleId("R_RM");
        role.setRoleCode("CUST_MANAGER");
        role.setRoleChName("客户经理");
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(new java.util.ArrayList<>(List.of(role)));

        LoginRespDTO resp = authService.login("admin", "pass", session);

        assertThat(resp.getMainOrgCode()).isNull();
        assertThat(resp.getRoles()).hasSize(1);
    }

    @Test
    void login_shouldLockAccountAtExactThreshold() {
        PtUser user = makeEnabledUser("E001", "admin", "hashed");
        user.setPassWrongCount(4); // next wrong = 5 = MAX_WRONG_COUNT
        when(userMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin", "wrong", session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40106"));
        verify(userMapper).updateLockedStatus("E001", 1);
    }

    @Test
    void getCurrentUser_shouldThrowWhenSessionInvalid() {
        when(session.getAttribute("currentUser")).thenReturn(null);

        assertThatThrownBy(() -> authService.getCurrentUser(session))
            .isInstanceOf(AuthException.class)
            .satisfies(e -> assertThat(((AuthException) e).getCode()).isEqualTo("AUTH-40105"));
    }

    @Test
    void getCurrentUser_shouldReturnContextWhenValid() {
        CurrentUserContext ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001",
            "总行", 1, Set.of("R1"), Set.of("ADMIN"), Set.of(), false);
        when(session.getAttribute("currentUser")).thenReturn(ctx);

        CurrentUserContext result = authService.getCurrentUser(session);
        assertThat(result.empId()).isEqualTo("E001");
    }

    @Test
    void getUserPermissions_shouldBatchQueryResources_noN1() {
        // 两个角色共 3 个资源 ID，应一次性批量查 PT_RESOURCE，而非逐条 selectByResourceId（N+1）
        PtRole r1 = new PtRole(); r1.setRoleId("R1"); r1.setRoleCode("ROLE_A");
        PtRole r2 = new PtRole(); r2.setRoleId("R2"); r2.setRoleCode("ROLE_B");
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(List.of(r1, r2));
        when(cacheService.getResourceIdsByRoleId("R1")).thenReturn(Set.of("RES_1", "RES_2"));
        when(cacheService.getResourceIdsByRoleId("R2")).thenReturn(Set.of("RES_2", "RES_3"));
        // 一次批量查返回启用资源 URL（RES_3 假设被禁用，不在结果里）
        when(resourceMapper.selectEnabledUrlsByResourceIds(any())).thenReturn(List.of("/api/a", "/api/b"));
        when(bizScopeService.getUserBizScopes("E001")).thenReturn(java.util.Map.of());

        var dto = authService.getUserPermissions("E001");

        assertThat(dto.getResourceUrls()).containsExactlyInAnyOrder("/api/a", "/api/b");
        assertThat(dto.getRoleCodes()).containsExactlyInAnyOrder("ROLE_A", "ROLE_B");
        // 关键：不再逐条单查 → 无 N+1
        verify(resourceMapper, never()).selectByResourceId(anyString());
    }

    @Test
    void getUserPermissions_nullRoles_shouldReturnEmptyCollections() {
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(null);
        when(bizScopeService.getUserBizScopes("E001")).thenReturn(java.util.Map.of());

        var dto = authService.getUserPermissions("E001");

        assertThat(dto.getRoleIds()).isEmpty();
        assertThat(dto.getRoleCodes()).isEmpty();
        assertThat(dto.getResourceUrls()).isEmpty();
        verifyNoInteractions(cacheService, resourceMapper);
    }
}
