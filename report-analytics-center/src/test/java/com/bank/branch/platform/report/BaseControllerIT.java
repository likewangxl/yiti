package com.bank.branch.platform.report;

import com.bank.branch.platform.auth.security.interceptor.AuthorizationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Controller 集成测试基类（F11 修补：PT_RESOURCE 时序前置）.
 *
 * <p><b>F11 背景</b>：M5.4.2 / M1.6 / M2.4 / M3.4 / M4.4 阶段才集中注册 PT_RESOURCE，
 * 但每个 Controller 阶段（M1.x ~ M5.x）的端到端 IT 在 PT_RESOURCE 行尚未写入时会跑，
 * 将被 {@link AuthorizationInterceptor}（auth-permission-center 的 MVC 拦截器）
 * 以 "resource_id not found → AUTH-40302" 拒绝。
 *
 * <p><b>解决约定 A（首选）</b>：所有 {@code *ControllerIT} 继承本基类，
 * {@code @MockBean AuthorizationInterceptor} 全放行，不依赖 PT_RESOURCE 表已写入.
 *
 * <p>备选约定 B：使用 {@code @Sql(scripts = "/sql/test-pt-resources-rpt.sql")} 提前
 * INSERT 测试用 PT_RESOURCE 行（M0.6 创建空骨架，每个 Controller IT 阶段按需追加）.
 *
 * <p>生产 Flyway {@code V1_0_X__rpt_*_pt_resources.sql}（M1.6 / M2.4 / M3.4 / M4.4 / M5.4
 * 集中注册）继续保留作为生产数据，IT 仅走本 mock 旁路.
 *
 * <p><b>注意</b>：plan 文档里把拦截器类名写成 {@code BizAuthInterceptor}，实际 auth-permission-center
 * 导出的类名是 {@link AuthorizationInterceptor}（{@code com.bank.branch.platform.auth.security.interceptor}
 * 包下），本基类使用正确的类名.
 *
 * <p><b>用法</b>：
 * <pre>{@code
 * class MyControllerIT extends BaseControllerIT {
 *     @Test
 *     void test() throws Exception {
 *         mvc.perform(get("/api/reports/..."))
 *            .andExpect(status().isOk());
 *     }
 * }
 * }</pre>
 */
@SpringBootTest(classes = ReportTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseControllerIT {

    @Autowired
    protected MockMvc mvc;

    /**
     * Mock 掉 auth-permission-center 的 AuthorizationInterceptor，默认全放行。
     * 具体 IT 需要测"权限拒绝"场景时，可在测试方法里覆盖 {@code when(...).thenReturn(false)}.
     */
    @MockBean
    protected AuthorizationInterceptor authorizationInterceptor;

    @BeforeEach
    void setUpAuthorization() throws Exception {
        when(authorizationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }
}
