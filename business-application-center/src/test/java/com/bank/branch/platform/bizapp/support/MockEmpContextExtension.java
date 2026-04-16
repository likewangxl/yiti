package com.bank.branch.platform.bizapp.support;

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
 * 1. 从 Spring ApplicationContext 拿到 @MockBean(CurrentUserApi.class) 实例，用 Mockito 配置返回值
 * 2. 用静态方法 DataScopeContext.set(...) 设置 ThreadLocal
 * 3. afterEach 清理 DataScopeContext.clear()
 */
public class MockEmpContextExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Method m = context.getRequiredTestMethod();
        WithMockEmpContext anno = m.getAnnotation(WithMockEmpContext.class);
        if (anno == null) return;

        // 从 Spring 容器中拿到 mock 的 CurrentUserApi（由 AbstractControllerIntegrationTest 通过 @MockBean 注册）
        CurrentUserApi currentUserApi = SpringExtension.getApplicationContext(context)
                .getBean(CurrentUserApi.class);
        Set<String> roleCodeSet = new HashSet<>(Arrays.asList(anno.roleCodes()));
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn(anno.empId());
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn(anno.orgCode());
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(roleCodeSet);
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(anno.systemAdmin());

        // 设置 DataScopeContext ThreadLocal（默认使用 LOAN BizType，具体测试可覆盖）
        DataScopeContext scopeCtx = new DataScopeContext();
        scopeCtx.setBizType(BizType.LOAN);
        scopeCtx.setAction(BizAction.LIST);
        scopeCtx.setScope(DataScopeType.valueOf(anno.dataScope()));
        scopeCtx.setEmpId(anno.empId());
        scopeCtx.setOrgCode(anno.orgCode());
        scopeCtx.setOrgSubtreeCodes(new HashSet<>(Arrays.asList(anno.orgSubtree())));
        DataScopeContext.set(scopeCtx);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        DataScopeContext.clear();
    }
}
