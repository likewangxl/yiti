package com.bank.branch.platform.report;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.MetricQueryApi;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 跨模块上游 Api 的 Mock 兜底配置（M1.5 Mapper IT 启用）.
 *
 * <p>背景：Spring 启动 {@link ReportTestApplication} 时会扫描 controller/service/facade 包，
 * 这些类的构造器会注入跨模块 *Api。Mapper IT 不需要这些 Bean 的真实实现，
 * 但 context 装配阶段缺 bean 会失败。
 *
 * <p>解决：本配置类提供所有上游 Api 的 Mockito mock bean（默认空行为）作为兜底，
 * 由 {@link ReportTestApplication#TestUpstreamApiMockConfig} 间接 import 自动激活。
 *
 * <p>BaseControllerIT 仍然继续用 {@code @MockBean} 显式声明（同名 bean 会覆盖本配置的兜底）。
 */
@Configuration
public class TestUpstreamApiMockConfig {

    @Bean
    public MetricApi metricApi() {
        return Mockito.mock(MetricApi.class);
    }

    /**
     * DynamicQueryServiceImpl EMP 维度 N+1 修复新增依赖，补齐兜底 mock（同上 UserApi 场景）.
     */
    @Bean
    public MetricQueryApi metricQueryApi() {
        return Mockito.mock(MetricQueryApi.class);
    }

    @Bean
    public KpiApi kpiApi() {
        return Mockito.mock(KpiApi.class);
    }

    @Bean
    public DictApi dictApi() {
        return Mockito.mock(DictApi.class);
    }

    @Bean
    public BizScopeApi bizScopeApi() {
        return Mockito.mock(BizScopeApi.class);
    }

    @Bean
    public CurrentUserApi currentUserApi() {
        return Mockito.mock(CurrentUserApi.class);
    }

    @Bean
    public OrgApi orgApi() {
        return Mockito.mock(OrgApi.class);
    }

    @Bean
    public CustomerQueryApi customerQueryApi() {
        return Mockito.mock(CustomerQueryApi.class);
    }

    @Bean
    public TouchTaskQueryApi touchTaskQueryApi() {
        return Mockito.mock(TouchTaskQueryApi.class);
    }

    @Bean
    public AuditApi auditApi() {
        return Mockito.mock(AuditApi.class);
    }

    @Bean
    public FileApi fileApi() {
        return Mockito.mock(FileApi.class);
    }

    /**
     * AllocPreviewService（业绩调整分配预览）依赖，补齐兜底 mock（原漏配，导致
     * 任何启用完整 ReportTestApplication 上下文的测试装配阶段找不到该 Bean 而失败）.
     */
    @Bean
    public AllocApi allocApi() {
        return Mockito.mock(AllocApi.class);
    }

    /**
     * DynamicQueryServiceImpl 依赖，补齐兜底 mock（原漏配，同上 AllocApi 场景）.
     */
    @Bean
    public UserApi userApi() {
        return Mockito.mock(UserApi.class);
    }
}
