package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

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

    /**
     * 测试用 CustomerQueryApi (V1.2 Q2 新增)：
     * 因 PerfTestApp 仅扫描 performance 子包，customer-marketing-center 的
     * {@code CustomerQueryApiImpl} 不会被自动装配。所有测试默认返回"客户存在"，
     * 单测需覆盖可 {@code @MockBean} 替换。
     */
    @Bean
    @Primary
    public CustomerQueryApi customerQueryApi() {
        CustomerQueryApi m = Mockito.mock(CustomerQueryApi.class);
        CustomerDTO defaultCust = new CustomerDTO();
        defaultCust.setId("DEFAULT_MOCK_CUST");
        Mockito.when(m.getCustomer(Mockito.anyString()))
                .thenReturn(Optional.of(defaultCust));
        Mockito.when(m.isValidCustomer(Mockito.anyString())).thenReturn(true);
        return m;
    }

    /**
     * 测试用 WorkflowApi (V1.2 Q2 新增)：
     * 因 PerfTestApp 仅扫描 performance 子包，workflow-center 的
     * {@code WorkflowFacade} 不会被自动装配。所有测试默认返回固定的
     * {@code WorkflowLaunchResp}；单测需覆盖可 {@code @MockBean} 替换。
     */
    @Bean
    @Primary
    public WorkflowApi workflowApi() {
        WorkflowApi m = Mockito.mock(WorkflowApi.class);
        Mockito.when(m.startProcess(Mockito.any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_MOCK_DEFAULT", null, null));
        return m;
    }
}
