package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.AuthException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @InjectMocks AuthService authService;

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
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(List.of(role));

        LoginRespDTO resp = authService.login("admin", "pass", session);

        assertThat(resp.getEmpId()).isEqualTo("E001");
        assertThat(resp.getMainOrgCode()).isEqualTo("ORG001");
        assertThat(resp.getRoles()).hasSize(1);
        verify(userMapper).updatePassWrongCount("E001", 0); // reset on success
        verify(session).setAttribute(eq("currentUser"), any(CurrentUserContext.class));
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
        when(userRoleMapper.selectRolesByUserId("E001")).thenReturn(List.of());

        LoginRespDTO resp = authService.login("admin", "pass", session);

        assertThat(resp.getMainOrgCode()).isNull();
        assertThat(resp.getRoles()).isEmpty();
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
}
