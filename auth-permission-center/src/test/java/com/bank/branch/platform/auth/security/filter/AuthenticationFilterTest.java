package com.bank.branch.platform.auth.security.filter;

import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        lenient().when(userMapper.selectByUserId("E001")).thenReturn(fresh);
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

    private CurrentUserContext makeCtx(String empId) {
        return new CurrentUserContext(empId, empId, "测试用户", "ORG001", "总行", 1,
            Set.of("R_RM"), Set.of("CUST_MANAGER"), Set.of("ROLE:CUST_MANAGER"), false);
    }
}
