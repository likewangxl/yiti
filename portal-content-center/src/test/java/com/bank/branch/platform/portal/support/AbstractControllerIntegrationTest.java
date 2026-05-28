package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.portal.service.AddressBookService;
import com.bank.branch.platform.portal.service.DocService;
import com.bank.branch.platform.portal.service.NavService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.service.ShortcutService;
import com.bank.branch.platform.portal.service.WorkspaceService;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Controller 集成测试基类
 * - SpringBootTest with MockMvc
 * - @MockBean 替换所有跨模块 Api（auth + governance），让测试不依赖真实模块启动
 * - @WithMockEmpContext 通过 SpringExtension 拿到 mock 的 CurrentUserApi 配置返回值
 *
 * Mapper 不在这里 mock —— Controller 测试中的 ProductService 等业务 Service 由
 * 子类自己 @MockBean 替换，避免拖入数据库依赖。
 *
 * <p>由于 portal-content-center 模块没有 @SpringBootApplication 类（启动入口在 bootstrap 模块），
 * 此基类内嵌 {@link TestApp} 作为最小化 Spring Boot 入口，仅扫描 portal 包即可。</p>
 */
@Tag("integration")
@SpringBootTest(classes = AbstractControllerIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractControllerIntegrationTest {

    /**
     * 最小化 Spring Boot 测试入口
     * 仅扫描 controller 包，业务 Service 由各子类通过 @MockBean 提供，
     * 避免拉起 mapper / mybatis / flowable 等与 Controller 测试无关的基础设施。
     *
     * <p>通过 @SpringBootTest(classes=...) 显式引用，确保子类在任意包路径下都能正确发现此配置。</p>
     */
    @SpringBootApplication(
            scanBasePackages = "com.bank.branch.platform.portal.controller",
            excludeName = {
                    "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                    "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                    "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                    "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
                    "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                    "org.flowable.spring.boot.ProcessEngineAutoConfiguration",
                    "org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration",
                    "org.flowable.spring.boot.app.AppEngineAutoConfiguration",
                    "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration",
                    "org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration",
                    "org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration",
                    "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration",
                    "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration",
                    "org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration",
                    "org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration",
                    "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration",
                    "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration",
                    "org.flowable.spring.boot.RestApiAutoConfiguration",
                    "org.flowable.spring.boot.FlowableJpaAutoConfiguration"
            }
    )
    static class TestApp {
    }

    @MockBean protected CurrentUserApi currentUserApi;
    @MockBean protected BizScopeApi bizScopeApi;
    @MockBean protected OrgApi orgApi;
    @MockBean protected DictApi dictApi;
    @MockBean protected FileApi fileApi;
    @MockBean protected NotifyApi notifyApi;
    @MockBean protected AuditApi auditApi;
    @MockBean protected AddressBookService addressBookService;
    @MockBean protected DocService docService;
    @MockBean protected NavService navService;
    @MockBean protected ProductService productService;
    @MockBean protected ProductExportService productExportService;
    @MockBean protected ShortcutService shortcutService;
    @MockBean protected WorkspaceService workspaceService;
}
