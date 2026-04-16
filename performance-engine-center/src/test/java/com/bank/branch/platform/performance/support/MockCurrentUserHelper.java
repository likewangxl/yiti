package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import org.mockito.Mockito;

import java.util.Set;

/**
 * CurrentUserApi Mock 辅助类.
 * <p>用于 Service/Facade 单元测试中快速构造管理员 / 普通员工上下文.
 */
public final class MockCurrentUserHelper {

    private MockCurrentUserHelper() {
    }

    /** 构造系统管理员 mock (isSystemAdmin=true, empId=admin, orgCode=HQ). */
    public static CurrentUserApi mockAdmin() {
        CurrentUserApi m = Mockito.mock(CurrentUserApi.class);
        Mockito.when(m.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(m.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(m.getCurrentRoleIds()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(m.getCurrentRoleCodes()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(m.getCurrentCandidateGroupKeys()).thenReturn(Set.of());
        Mockito.when(m.isSystemAdmin()).thenReturn(true);
        return m;
    }

    /** 构造普通员工 mock. */
    public static CurrentUserApi mockEmp(String empId, String orgCode) {
        CurrentUserApi m = Mockito.mock(CurrentUserApi.class);
        Mockito.when(m.getCurrentEmpId()).thenReturn(empId);
        Mockito.when(m.getCurrentOrgCode()).thenReturn(orgCode);
        Mockito.when(m.getCurrentRoleIds()).thenReturn(Set.of());
        Mockito.when(m.getCurrentRoleCodes()).thenReturn(Set.of());
        Mockito.when(m.getCurrentCandidateGroupKeys()).thenReturn(Set.of());
        Mockito.when(m.isSystemAdmin()).thenReturn(false);
        return m;
    }
}
