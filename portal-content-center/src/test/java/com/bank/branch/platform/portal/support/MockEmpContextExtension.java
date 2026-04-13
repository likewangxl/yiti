package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * JUnit 5 扩展：
 *  1. 从 Spring ApplicationContext 拿到 @MockBean(CurrentUserApi.class) 实例，
 *     用 Mockito 配置返回值
 *  2. 用静态方法 DataScopeContext.set(...) 设置 ThreadLocal
 *  3. afterEach 清理 DataScopeContext.clear()
 *
 * 不直接操作 CurrentUserProvider 的 ThreadLocal —— 因为它是 Spring @Component 实例，
 * 而且业务代码用的是 CurrentUserApi（mock 接口比 mock 实例上下文更稳定）
 */
public class MockEmpContextExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Method m = context.getRequiredTestMethod();
        WithMockEmpContext anno = m.getAnnotation(WithMockEmpContext.class);
        if (anno == null) return;

        // 1. 拿到 Spring 容器中 mock 的 CurrentUserApi（由 AbstractControllerIntegrationTest 通过 @MockBean 注册）
        CurrentUserApi currentUserApi = SpringExtension.getApplicationContext(context)
            .getBean(CurrentUserApi.class);
        Set<String> roleCodeSet = new HashSet<>(Arrays.asList(anno.roleCodes()));
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn(anno.empId());
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn(anno.orgCode());
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(roleCodeSet);
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(anno.systemAdmin());

        // 2. 设置 DataScopeContext (static method on common-security DataScopeContext)
        DataScopeContext scopeCtx = new DataScopeContext();
        scopeCtx.setBizType(BizType.PRODUCT);  // 默认 PRODUCT，业务测试可根据需要改造
        scopeCtx.setAction(BizAction.LIST);
        scopeCtx.setScope(DataScopeType.valueOf(anno.dataScope()));
        scopeCtx.setEmpId(anno.empId());
        scopeCtx.setOrgCode(anno.orgCode());
        scopeCtx.setOrgSubtreeCodes(new HashSet<>(Arrays.asList(anno.orgSubtree())));
        DataScopeContext.set(scopeCtx);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // 1. 清理 DataScopeContext ThreadLocal
        DataScopeContext.clear();
        // 2. CurrentUserApi mock 由 @MockBean 自动 reset，不需手动清理
    }
}
