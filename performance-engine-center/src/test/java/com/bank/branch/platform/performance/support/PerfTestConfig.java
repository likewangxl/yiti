package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试环境 Bean 装配: 因 PerfTestApp 只扫描 performance 子包,
 * auth 的真实 Bean 不会被加载, 这里补充 mock 版 CurrentUserApi / BizScopeApi 供业务 Bean 注入.
 *
 * <p>说明: PerfRunTaskService (Task 4.2) 引入了 BizScopeApi 依赖, 若不在此提供 mock bean,
 * 所有继承 PerformanceMapperTestBase / PerformanceConcurrentTestBase 的 IT 加载 Spring Context 时
 * 将因 "No qualifying bean of type BizScopeApi" 失败.
 */
@TestConfiguration
public class PerfTestConfig {

    /** 测试用管理员 CurrentUserApi (empId=admin, isSystemAdmin=true). */
    @Bean
    @Primary
    public CurrentUserApi currentUserApi() {
        return MockCurrentUserHelper.mockAdmin();
    }

    /**
     * 测试用 BizScopeApi: 默认所有 empId 对 PERF_CONFIG 返回 ALL (管理员全见),
     * 使 Mapper IT 不受数据范围过滤影响; 单测若需覆盖可以 {@code @MockBean} 替换.
     */
    @Bean
    @Primary
    public BizScopeApi bizScopeApi() {
        BizScopeApi m = Mockito.mock(BizScopeApi.class);
        Mockito.when(m.resolveScope(Mockito.anyString(), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.ALL);
        return m;
    }
}
