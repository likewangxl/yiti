package com.bank.branch.platform.auth.security.interceptor;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.security.matcher.ResourceMatcher;
import com.bank.branch.platform.auth.security.resolver.BizMeta;
import com.bank.branch.platform.auth.security.resolver.BizMetaResolver;
import com.bank.branch.platform.auth.security.resolver.RbacAuthorizer;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 授权拦截器
 * 执行 RBAC 资源授权 + BizType 数据范围解析，按以下顺序检查：
 * 1. ResourceMatcher: URL + Method -> PT_RESOURCE（未注册 → 403 AUTH-40302）
 * 2. RbacAuthorizer:  角色资源授权校验（失败 → 403 AUTH-40301，SYS_ADMIN 跳过）
 * 3. BizMetaResolver: @BizAuth 注解解析（未声明 → 403 AUTH-40304）
 * 4. BizScopeApi:     构建 DataScopeContext 并放入 ThreadLocal
 * afterCompletion 中清理 DataScopeContext ThreadLocal。
 */
@Slf4j
@RequiredArgsConstructor
public class AuthorizationInterceptor implements HandlerInterceptor {

    private final ResourceMatcher resourceMatcher;
    private final RbacAuthorizer rbacAuthorizer;
    private final BizMetaResolver bizMetaResolver;
    private final BizScopeApi bizScopeApi;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Step 1: 资源匹配
        Optional<PtResource> resourceOpt = resourceMatcher.match(uri, method);
        if (resourceOpt.isEmpty()) {
            writeForbidden(response, AuthErrorCode.RESOURCE_NOT_REGISTERED.getCode(),
                AuthErrorCode.RESOURCE_NOT_REGISTERED.getMessage());
            return false;
        }
        String resourceId = resourceOpt.get().getResourceId();

        CurrentUserContext userCtx = currentUserProvider.get();
        String empId = userCtx.empId();

        // Step 2: RBAC 校验（SYS_ADMIN 跳过）
        if (!userCtx.systemAdmin()) {
            if (!rbacAuthorizer.authorize(empId, resourceId)) {
                writeForbidden(response, AuthErrorCode.RBAC_DENIED.getCode(),
                    AuthErrorCode.RBAC_DENIED.getMessage());
                return false;
            }
        }

        // Step 3: 解析 @BizAuth 元数据（缺失时放行，使用仅包含 empId 的最小上下文）
        Optional<BizMeta> bizMetaOpt = bizMetaResolver.resolve(handler);
        if (bizMetaOpt.isEmpty()) {
            com.bank.branch.platform.common.security.context.DataScopeContext minCtx =
                new com.bank.branch.platform.common.security.context.DataScopeContext();
            minCtx.setEmpId(empId);
            minCtx.setOrgCode(userCtx.mainOrgCode());
            minCtx.setCandidateGroupKeys(userCtx.candidateGroupKeys());
            com.bank.branch.platform.common.security.context.DataScopeContext.set(minCtx);
            log.debug("[AuthInterceptor] 接口未声明@BizAuth，放行 empId={}, resourceId={}", empId, resourceId);
            return true;
        }
        BizMeta bizMeta = bizMetaOpt.get();

        // Step 4: 构建 DataScopeContext 并放入 ThreadLocal
        DataScopeContext apiCtx = bizScopeApi.buildScopeContext(empId, bizMeta.bizType(), bizMeta.action());
        com.bank.branch.platform.common.security.context.DataScopeContext commonCtx =
            toCommonDataScopeContext(apiCtx, userCtx);
        com.bank.branch.platform.common.security.context.DataScopeContext.set(commonCtx);

        log.debug("[AuthInterceptor] 授权通过 empId={}, resourceId={}, scope={}", empId, resourceId, apiCtx.scopeType());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 DataScopeContext ThreadLocal，防止线程池复用时数据污染
        com.bank.branch.platform.common.security.context.DataScopeContext.clear();
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将 auth 模块的 DataScopeContext DTO record 转换为 common-security 的 DataScopeContext（ThreadLocal 版本）
     */
    private com.bank.branch.platform.common.security.context.DataScopeContext toCommonDataScopeContext(
        DataScopeContext apiCtx, CurrentUserContext userCtx) {
        com.bank.branch.platform.common.security.context.DataScopeContext ctx =
            new com.bank.branch.platform.common.security.context.DataScopeContext();
        ctx.setScope(apiCtx.scopeType());
        ctx.setEmpId(apiCtx.empId());
        ctx.setOrgCode(apiCtx.orgCode());
        ctx.setOrgSubtreeCodes(apiCtx.orgSubtreeCodes());
        ctx.setBizType(apiCtx.bizType());
        ctx.setAction(apiCtx.action());
        ctx.setCandidateGroupKeys(userCtx.candidateGroupKeys());
        return ctx;
    }

    /** 向客户端写入 HTTP 403 标准错误响应 */
    private void writeForbidden(HttpServletResponse response, String code, String message)
        throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ResponseWrapper<?> body = ResponseWrapper.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
