package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WithMockEmpContextSelfTest extends AbstractControllerIntegrationTest {

    @Test
    @WithMockEmpContext(empId = "E99999", orgCode = "ORG_TEST",
                        dataScope = "ORG", orgSubtree = {"ORG_TEST"})
    void shouldInjectMockContext() {
        // 1. CurrentUserApi mock 已配置
        assertThat(currentUserApi.getCurrentEmpId()).isEqualTo("E99999");
        assertThat(currentUserApi.getCurrentOrgCode()).isEqualTo("ORG_TEST");

        // 2. DataScopeContext ThreadLocal 已设置（注意字段是 scope 不是 scopeType）
        DataScopeContext ctx = DataScopeContext.current();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getScope()).isEqualTo(DataScopeType.ORG);
        assertThat(ctx.getOrgCode()).isEqualTo("ORG_TEST");
    }
}
