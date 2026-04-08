package com.bank.branch.platform.auth.security.interceptor;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.security.matcher.ResourceMatcher;
import com.bank.branch.platform.auth.security.resolver.BizMeta;
import com.bank.branch.platform.auth.security.resolver.BizMetaResolver;
import com.bank.branch.platform.auth.security.resolver.RbacAuthorizer;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationInterceptorTest {

    @Mock ResourceMatcher resourceMatcher;
    @Mock RbacAuthorizer rbacAuthorizer;
    @Mock BizMetaResolver bizMetaResolver;
    @Mock BizScopeApi bizScopeApi;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock ObjectMapper objectMapper;
    @InjectMocks AuthorizationInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HandlerMethod handler;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        handler = new HandlerMethod(new TestController(),
            TestController.class.getMethod("readCustomers"));
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(currentUserProvider.get()).thenReturn(makeCtx("E001", false));
    }

    @AfterEach
    void tearDown() {
        com.bank.branch.platform.common.security.context.DataScopeContext.clear();
    }

    @Test
    void preHandle_resourceNotFound_returns403() throws Exception {
        request.setRequestURI("/api/customers");
        request.setMethod("GET");
        when(resourceMatcher.match("/api/customers", "GET")).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_rbacDenied_returns403() throws Exception {
        PtResource res = makeResource("RES_01");
        when(resourceMatcher.match(any(), any())).thenReturn(Optional.of(res));
        when(rbacAuthorizer.authorize("E001", "RES_01")).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_systemAdmin_skipRbacCheck() throws Exception {
        // 系统管理员跳过 RBAC 校验
        when(currentUserProvider.get()).thenReturn(makeCtx("ADMIN", true));
        PtResource res = makeResource("RES_01");
        when(resourceMatcher.match(any(), any())).thenReturn(Optional.of(res));
        when(bizMetaResolver.resolve(handler)).thenReturn(Optional.of(new BizMeta(BizType.CUSTOMER, BizAction.READ)));
        DataScopeContext ctx = new DataScopeContext(DataScopeType.ALL, "ADMIN", "ORG001", Set.of(), BizType.CUSTOMER, BizAction.READ);
        when(bizScopeApi.buildScopeContext("ADMIN", BizType.CUSTOMER, BizAction.READ)).thenReturn(ctx);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        verify(rbacAuthorizer, never()).authorize(any(), any());
    }

    @Test
    void preHandle_noBizAuthAnnotation_returns403() throws Exception {
        PtResource res = makeResource("RES_01");
        when(resourceMatcher.match(any(), any())).thenReturn(Optional.of(res));
        when(rbacAuthorizer.authorize("E001", "RES_01")).thenReturn(true);
        when(bizMetaResolver.resolve(handler)).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_success_setsDataScopeContext() throws Exception {
        PtResource res = makeResource("RES_01");
        when(resourceMatcher.match(any(), any())).thenReturn(Optional.of(res));
        when(rbacAuthorizer.authorize("E001", "RES_01")).thenReturn(true);
        when(bizMetaResolver.resolve(handler)).thenReturn(Optional.of(new BizMeta(BizType.CUSTOMER, BizAction.READ)));
        DataScopeContext ctx = new DataScopeContext(DataScopeType.ORG, "E001", "ORG001", Set.of(), BizType.CUSTOMER, BizAction.READ);
        when(bizScopeApi.buildScopeContext("E001", BizType.CUSTOMER, BizAction.READ)).thenReturn(ctx);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        // DataScopeContext 应已被放入 ThreadLocal
        com.bank.branch.platform.common.security.context.DataScopeContext commonCtx =
            com.bank.branch.platform.common.security.context.DataScopeContext.current();
        assertThat(commonCtx).isNotNull();
        assertThat(commonCtx.getScope()).isEqualTo(DataScopeType.ORG);
        assertThat(commonCtx.getEmpId()).isEqualTo("E001");
    }

    @Test
    void afterCompletion_clearsDataScopeContext() throws Exception {
        // 预设有 DataScopeContext
        com.bank.branch.platform.common.security.context.DataScopeContext dsc =
            new com.bank.branch.platform.common.security.context.DataScopeContext();
        com.bank.branch.platform.common.security.context.DataScopeContext.set(dsc);

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(com.bank.branch.platform.common.security.context.DataScopeContext.current()).isNull();
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    private PtResource makeResource(String id) {
        PtResource r = new PtResource();
        r.setResourceId(id);
        r.setResourceUrl("/api/customers");
        r.setResourceMethod("GET");
        r.setStatus(0);
        return r;
    }

    private CurrentUserContext makeCtx(String empId, boolean isAdmin) {
        return new CurrentUserContext(empId, empId, "测试用户", "ORG001", "总行", 1,
            Set.of("R_RM"), Set.of("CUST_MANAGER"), Set.of("ROLE:CUST_MANAGER"), isAdmin);
    }

    static class TestController {
        @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.READ)
        public void readCustomers() {}
    }
}
