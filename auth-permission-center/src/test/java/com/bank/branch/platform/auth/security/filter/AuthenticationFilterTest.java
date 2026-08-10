package com.bank.branch.platform.auth.security.filter;

import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.common.web.exception.AuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock CurrentUserProvider currentUserProvider;
    @Mock FilterChain filterChain;
    @Mock ObjectMapper objectMapper;
    @Mock UserMapper userMapper;
    @Mock AuthService authService;
    @InjectMocks AuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        // 401 响应需要序列化 ResponseWrapper，mock 默认返回 null → NPE
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        // 过滤器会按主键实时重查用户状态，返回启用且未锁定的用户以放行有效会话
        PtUser fresh = new PtUser();
        fresh.setUserId("E001");
        fresh.setIsEnabled(0); // 0=启用
        fresh.setIsLocked(0);
        fresh.setIsExpired(0);
        lenient().when(userMapper.selectByUserId("E001")).thenReturn(fresh);
        lenient().when(authService.refreshRoleContext(any(CurrentUserContext.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void doFilter_whitelistedUrl_passesThroughWithoutSettingContext() throws Exception {
        request.setRequestURI("/api/auth/login");
        request.setMethod("POST");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(currentUserProvider, never()).set(any());
    }

    @Test
    void doFilter_validSession_setsContextAndContinues() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext ctx = makeCtx("E001");
        session.setAttribute(AuthService.SESSION_USER_KEY, ctx);
        request.setSession(session);

        filter.doFilterInternal(request, response, filterChain);

        verify(currentUserProvider).set(ctx);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_noSession_returns401() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        // no session set on request → getSession(false) returns null

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_sessionWithInvalidAttr_returns401() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_KEY, "not-a-context");
        request.setSession(session);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_validSession_clearsContextInFinally() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_KEY, makeCtx("E001"));
        request.setSession(session);

        filter.doFilterInternal(request, response, filterChain);

        verify(currentUserProvider).clear();
    }

    @Test
    void doFilter_legacySingleRoleSession_refreshesUnionContextAndUpgradesSession() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext legacy = new CurrentUserContext(
                "E001", "E001", "测试用户", "ORG001", "总行", 1,
                Set.of("R_REPORTER"), Set.of("PARTY_REPORTER"),
                Set.of("ROLE:PARTY_REPORTER", "USER:E001", "ORG:ORG001"), false, "R_REPORTER");
        CurrentUserContext refreshed = new CurrentUserContext(
                "E001", "E001", "测试用户", "ORG001", "总行", 1,
                Set.of("R_REPORTER", "R_ADMIN"), Set.of("PARTY_REPORTER", "SYS_ADMIN"),
                Set.of("ROLE:PARTY_REPORTER", "ROLE:SYS_ADMIN", "USER:E001", "ORG:ORG001"), true, null);
        session.setAttribute(AuthService.SESSION_USER_KEY, legacy);
        request.setSession(session);
        when(authService.refreshRoleContext(legacy)).thenReturn(refreshed);

        filter.doFilterInternal(request, response, filterChain);

        verify(currentUserProvider).set(refreshed);
        verify(filterChain).doFilter(request, response);
        assertThat(session.getAttribute(AuthService.SESSION_USER_KEY)).isEqualTo(refreshed);
    }

    @Test
    void doFilter_revokedSystemAdmin_refreshesContextBeforeAuthorization() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext staleAdmin = new CurrentUserContext(
                "E001", "E001", "测试用户", "ORG001", "总行", 1,
                Set.of("R_ADMIN"), Set.of("SYS_ADMIN"), Set.of("ROLE:SYS_ADMIN"), true, null);
        CurrentUserContext refreshed = new CurrentUserContext(
                "E001", "E001", "测试用户", "ORG001", "总行", 1,
                Set.of("R_REPORTER"), Set.of("PARTY_REPORTER"), Set.of("ROLE:PARTY_REPORTER"), false, null);
        session.setAttribute(AuthService.SESSION_USER_KEY, staleAdmin);
        request.setSession(session);
        when(authService.refreshRoleContext(staleAdmin)).thenReturn(refreshed);

        filter.doFilterInternal(request, response, filterChain);

        verify(currentUserProvider).set(refreshed);
        assertThat(session.getAttribute(AuthService.SESSION_USER_KEY)).isEqualTo(refreshed);
    }

    @Test
    void doFilter_accountExpired_invalidatesSessionAndReturns401() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_KEY, makeCtx("E001"));
        request.setSession(session);
        PtUser expired = new PtUser();
        expired.setUserId("E001");
        expired.setIsEnabled(0);
        expired.setIsLocked(0);
        expired.setIsExpired(1);
        when(userMapper.selectByUserId("E001")).thenReturn(expired);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        verify(filterChain, never()).doFilter(any(), any());
        verify(authService, never()).refreshRoleContext(any());
    }

    @Test
    void doFilter_noValidRole_invalidatesSessionAndReturns401() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext ctx = makeCtx("E001");
        session.setAttribute(AuthService.SESSION_USER_KEY, ctx);
        request.setSession(session);
        when(authService.refreshRoleContext(ctx)).thenThrow(new AuthException(
                AuthErrorCode.USER_NO_ROLE.getCode(), AuthErrorCode.USER_NO_ROLE.getMessage()));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_nonIdentityAuthException_returns503AndPreservesSession() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext ctx = makeCtx("E001");
        session.setAttribute(AuthService.SESSION_USER_KEY, ctx);
        request.setSession(session);
        when(authService.refreshRoleContext(ctx)).thenThrow(new AuthException(
                AuthErrorCode.INTERNAL_ERROR.getCode(), AuthErrorCode.INTERNAL_ERROR.getMessage()));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(AuthService.SESSION_USER_KEY)).isEqualTo(ctx);
        verify(filterChain, never()).doFilter(any(), any());
        verify(currentUserProvider, never()).set(any());
    }

    @Test
    void doFilter_roleDatabaseUnavailable_returns503AndPreservesSession() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext ctx = makeCtx("E001");
        session.setAttribute(AuthService.SESSION_USER_KEY, ctx);
        request.setSession(session);
        when(authService.refreshRoleContext(ctx))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(AuthService.SESSION_USER_KEY)).isEqualTo(ctx);
        verify(filterChain, never()).doFilter(any(), any());
        verify(currentUserProvider, never()).set(any());
        ArgumentCaptor<ResponseWrapper<?>> bodyCaptor = ArgumentCaptor.forClass(ResponseWrapper.class);
        verify(objectMapper).writeValueAsString(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().getCode()).isEqualTo("AUTH-50301");
    }

    @Test
    void doFilter_userDatabaseUnavailable_returns503AndPreservesSession() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        MockHttpSession session = new MockHttpSession();
        CurrentUserContext ctx = makeCtx("E001");
        session.setAttribute(AuthService.SESSION_USER_KEY, ctx);
        request.setSession(session);
        when(userMapper.selectByUserId("E001"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(AuthService.SESSION_USER_KEY)).isEqualTo(ctx);
        verify(filterChain, never()).doFilter(any(), any());
        verify(authService, never()).refreshRoleContext(any());
    }

    private CurrentUserContext makeCtx(String empId) {
        return new CurrentUserContext(empId, empId, "测试用户", "ORG001", "总行", 1,
            Set.of("R_RM"), Set.of("CUST_MANAGER"), Set.of("ROLE:CUST_MANAGER"), false);
    }
}
