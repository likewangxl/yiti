package com.bank.branch.platform.bizapp.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Controller 集成测试基类。
 * <p>
 * 内嵌 TestApp 作为最小化 Spring Boot 启动类，仅扫描 bizapp 包，
 * 通过 @MockBean 替换所有跨模块 Api，使测试不依赖真实模块启动。
 * Redis 和 Flowable 相关的 AutoConfiguration 均被排除，使用 H2 内存库。
 * </p>
 */
@Tag("integration")
@SpringBootTest(classes = AbstractControllerIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractControllerIntegrationTest {

    /**
     * 最小化 Spring Boot 测试入口，仅扫描 bizapp 包。
     */
    @SpringBootApplication(
            scanBasePackages = "com.bank.branch.platform.bizapp",
            exclude = {
                    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                    org.flowable.spring.boot.ProcessEngineAutoConfiguration.class,
                    org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.app.AppEngineAutoConfiguration.class,
                    org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.idm.IdmEngineAutoConfiguration.class,
                    org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration.class,
                    org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration.class,
                    org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration.class,
                    org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration.class,
                    org.flowable.spring.boot.FlowableSecurityAutoConfiguration.class,
                    org.flowable.spring.boot.EndpointAutoConfiguration.class,
                    org.flowable.spring.boot.RestApiAutoConfiguration.class
            }
    )
    static class TestApp {
    }

    // auth-permission-center
    @MockBean protected CurrentUserApi currentUserApi;
    @MockBean protected BizScopeApi bizScopeApi;
    @MockBean protected OrgApi orgApi;

    // system-governance-center
    @MockBean protected DictApi dictApi;
    @MockBean protected FileApi fileApi;
    @MockBean protected NotifyApi notifyApi;
    @MockBean protected AuditApi auditApi;

    // workflow-center
    @MockBean protected WorkflowApi workflowApi;

    // customer-marketing-center
    @MockBean protected CustomerQueryApi customerQueryApi;
    @MockBean protected TouchTaskQueryApi touchTaskQueryApi;

    // portal-content-center
    @MockBean protected ProductApi productApi;
    @MockBean protected AddressBookApi addressBookApi;

    // Redis（命名 MockBean 防止与 Spring Session 冲突）
    @MockBean(name = "redisTemplate")
    protected RedisTemplate<String, Object> redisTemplate;
}
