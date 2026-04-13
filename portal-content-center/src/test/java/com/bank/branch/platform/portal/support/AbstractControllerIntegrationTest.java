package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.NotifyApi;
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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractControllerIntegrationTest {

    /**
     * 最小化 Spring Boot 测试入口
     * 仅扫描 portal 包，避免拉起 auth/governance/workflow 的真实 Bean
     */
    @SpringBootApplication(scanBasePackages = "com.bank.branch.platform.portal")
    static class TestApp {
    }

    @MockBean protected CurrentUserApi currentUserApi;
    @MockBean protected BizScopeApi bizScopeApi;
    @MockBean protected OrgApi orgApi;
    @MockBean protected DictApi dictApi;
    @MockBean protected FileApi fileApi;
    @MockBean protected NotifyApi notifyApi;
    @MockBean protected AuditApi auditApi;
}
