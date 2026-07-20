package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.PersonTagApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
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
     * 测试用 TodoQueryApi（分配/目标调整待办·已办查询引入）：
     * PerfApprovalCmdFacade / AllocAdjustTodoService / AllocAdjustDoneService 构造器注入
     * workflow-center 的 TodoQueryApi 按 businessKey 反查 Flowable 待办/已办；因 PerfTestApp
     * 仅扫描 performance 子包，workflow 真实 Bean 不会装配，加载完整上下文的 IT 会因
     * "No qualifying bean of type TodoQueryApi" 启动失败。此处提供默认空 mock
     * （List→空列表、Map→空表，Mockito 对集合返回类型的默认行为即空集合），
     * 需验证具体待办/已办行为的单测走各自 {@code @Mock}/{@code @MockBean} 覆盖。
     */
    @Bean
    @Primary
    public TodoQueryApi todoQueryApi() {
        return Mockito.mock(TodoQueryApi.class);
    }

    /**
     * 测试用 RoleApi（auth）：perf 主代码构造器注入按角色查询能力，PerfTestApp 仅扫描
     * performance 子包，auth 真实 Bean 不装配，加载完整上下文的 IT 会因缺此 Bean 启动失败。
     * 提供默认空 mock（集合返回类型默认空集合）；需断言行为的单测走各自 {@code @Mock}/{@code @MockBean}。
     */
    @Bean
    @Primary
    public RoleApi roleApi() {
        return Mockito.mock(RoleApi.class);
    }

    /**
     * 测试用 AuditApi（governance）：审计写入（@AuditLog 切面 / 业务显式调用）依赖此 Bean，
     * 测试上下文无 governance 真实 Bean，提供默认空 mock 避免上下文启动失败；单测可 {@code @MockBean} 覆盖。
     */
    @Bean
    @Primary
    public AuditApi auditApi() {
        return Mockito.mock(AuditApi.class);
    }

    /**
     * 测试用 DictApi（governance）：eval/导入等按字典码翻译走 DictApi，测试上下文无 governance 真实 Bean，
     * 提供默认空 mock 避免上下文启动失败；需断言字典行为的单测走各自 {@code @Mock}/{@code @MockBean}。
     */
    @Bean
    @Primary
    public DictApi dictApi() {
        return Mockito.mock(DictApi.class);
    }

    /**
     * 测试用 FileApi（governance）：导出/导入落 MinIO 的文件能力，测试上下文无 governance 真实 Bean，
     * 提供默认空 mock 避免上下文启动失败；单测可 {@code @MockBean} 覆盖。
     */
    @Bean
    @Primary
    public FileApi fileApi() {
        return Mockito.mock(FileApi.class);
    }

    /**
     * 测试用 NotifyApi（governance）：审批/任务通知能力，测试上下文无 governance 真实 Bean，
     * 提供默认空 mock 避免上下文启动失败；单测可 {@code @MockBean} 覆盖。
     */
    @Bean
    @Primary
    public NotifyApi notifyApi() {
        return Mockito.mock(NotifyApi.class);
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
     * 测试用 AddressBookApi (V1.12 微调引入)：
     * MetricResultImportStrategy 通过构造器注入 AddressBookApi 校验员工存在性，
     * 落到 portal 通讯录员工表 ADDRBOOK_EMPLOYEE（替换 V1.12 初版误用的 auth UserApi/PT_USER）。
     * 测试上下文无 portal-content-center 真实 Bean，
     * 此处提供默认放行 mock（返回非空 Optional&lt;EmployeeDTO&gt; 视作"员工存在"），
     * 单测可 {@code @MockBean} 覆盖具体行为。
     */
    @Bean
    @Primary
    public AddressBookApi addressBookApi() {
        AddressBookApi m = Mockito.mock(AddressBookApi.class);
        Mockito.when(m.getEmployee(Mockito.anyString()))
                .thenReturn(Optional.of(EmployeeDTO.builder().empId("ANY").build()));
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
     * 测试用 UserApi（2026-05-29 人员标签列表引入）：
     * EvalUserTagService 注入 UserApi 做用户分页/批量角色查询，测试上下文无真实 auth Bean，
     * 提供默认 mock 避免 Spring 上下文启动失败；单测可 @MockBean 覆盖。
     */
    @Bean
    @Primary
    public UserApi userApi() {
        UserApi m = Mockito.mock(UserApi.class);
        // 2026-06-15：TargetValueService 注入 UserApi 校验 EMP 工号存在性（PT_USER.username）；
        // 默认放行（返回非空 UserDTO 列表视作"工号存在"），单测/IT 可 @MockBean 覆盖。
        Mockito.when(m.getUsersByUsernames(Mockito.anyList()))
                .thenReturn(java.util.List.of(new UserDTO()));
        // 2026-06-25：导入校验（TargetValueService/MetricResultImportStrategy/KpiScoreImportStrategy）
        // 改用轻量 filterExistingUsernames 做存在性校验，默认放行（输入即视作全部存在）。
        Mockito.when(m.filterExistingUsernames(Mockito.anyList()))
                .thenAnswer(inv -> new java.util.ArrayList<>(inv.getArgument(0)));
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

    /**
     * 测试用 PersonTagApi（2026-07-20 KPI 方案「员工标签范围」引入）：
     * 因 PerfTestApp 仅扫描 performance 子包，governance 的 {@code PersonTagFacade}
     * 不会被自动装配。默认返回空（无标签成员 / 标签不存在），
     * 单测需覆盖可 {@code @MockBean} 替换。
     */
    @Bean
    @Primary
    public PersonTagApi personTagApi() {
        return Mockito.mock(PersonTagApi.class);
    }
}
