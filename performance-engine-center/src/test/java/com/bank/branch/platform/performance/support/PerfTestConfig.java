package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.Set;

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
     *
     * <p>V1.3 R1.3 补充：同步 mock {@code buildScopeContext}, 否则默认返回 null 会被
     * {@link com.bank.branch.platform.performance.service.scope.PerfScopeHelper} 判为
     * fail-close "1=0"，导致 V1.3 接入 pageWithScope 的 Controller IT（默认上下文 admin/ALL）
     * 读不到数据，破坏既有 TargetValue/TargetPlan Controller IT。
     */
    @Bean
    @Primary
    public BizScopeApi bizScopeApi() {
        BizScopeApi m = Mockito.mock(BizScopeApi.class);
        Mockito.when(m.resolveScope(Mockito.anyString(), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.ALL);
        // V1.3 R1.3：buildScopeContext 默认行为与 resolveScope 对齐 (ALL),
        // 让 PerfScopeHelper.getFragment 返回空片段（无 WHERE 附加条件）.
        Mockito.when(m.buildScopeContext(
                        Mockito.anyString(), Mockito.any(BizType.class), Mockito.any(BizAction.class)))
                .thenAnswer(inv -> new DataScopeContext(
                        DataScopeType.ALL,
                        inv.getArgument(0),
                        "HQ",
                        Set.of(),
                        inv.getArgument(1),
                        inv.getArgument(2)));
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

    /**
     * 测试用 WorkflowQueryApi (V1.4 S1.2 新增)：
     * PerfScopeHelper 的主构造器 2 参注入 WorkflowQueryApi 以支持
     * WORKFLOW_PARTICIPANT 真实查询。测试环境默认 mock 返回空集，
     * 使 WORKFLOW_PARTICIPANT 分支 fail-close（等价 V1.3 既有行为）；
     * 需要验证具体 WORKFLOW_PARTICIPANT 行为的单测可 {@code @MockBean} 覆盖。
     */
    @Bean
    @Primary
    public WorkflowQueryApi workflowQueryApi() {
        WorkflowQueryApi m = Mockito.mock(WorkflowQueryApi.class);
        Mockito.when(m.queryParticipatedBusinessKeys(
                        Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Set.of());
        return m;
    }

    /**
     * 测试用 JobApi (V1.7 P6 新增)：
     * MetricSchedulerService 通过构造器注入 JobApi，测试上下文无真实 governance Bean，
     * 此处提供空 mock 避免 Spring 上下文启动失败；
     * 需覆盖具体行为的测试可用 {@code @MockBean} 替换。
     */
    @Bean
    @Primary
    public JobApi jobApi() {
        return Mockito.mock(JobApi.class);
    }

    /**
     * 测试用 UserApi (V1.12 新增)：
     * MetricResultImportStrategy 通过构造器注入 UserApi 校验员工存在性，
     * 测试上下文无 auth-permission-center 真实 Bean，
     * 此处提供默认放行 mock（返回非空 UserDTO 视作"员工存在"），
     * 单测可 {@code @MockBean} 覆盖具体行为。
     */
    @Bean
    @Primary
    public UserApi userApi() {
        UserApi m = Mockito.mock(UserApi.class);
        Mockito.when(m.getUserByEmpId(Mockito.anyString())).thenReturn(new UserDTO());
        return m;
    }

    /**
     * 测试用 OrgApi (V1.12 新增)：
     * MetricResultImportStrategy 通过构造器注入 OrgApi 校验机构存在性，
     * 测试上下文无 auth-permission-center 真实 Bean，
     * 此处提供默认放行 mock（返回非空 OrgDTO 视作"机构存在"），
     * 单测可 {@code @MockBean} 覆盖具体行为。
     */
    @Bean
    @Primary
    public OrgApi orgApi() {
        OrgApi m = Mockito.mock(OrgApi.class);
        Mockito.when(m.getOrg(Mockito.anyString())).thenReturn(new OrgDTO());
        return m;
    }

    /**
     * 测试用 RedisTemplate&lt;String, String&gt; (V1.8 P6 新增)：
     * V1.7 引入的 KpiCascadeListener 构造器注入 RedisTemplate&lt;String, String&gt;
     * 用于 SETNX 30s 防重，但 perf 测试上下文无真实 Redis Bean 装配。
     * 此处提供空 mock 避免 E2E IT 加载 Spring 上下文时失败。
     * Spring DI 把泛型当作不同 bean 类型，必须显式提供（与 bootstrap TestMockConfig 同 pattern）。
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, String> stringRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }
}
