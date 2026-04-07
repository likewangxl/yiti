package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 5 - 全链路集成测试: 鉴权链路
 * 验证请求从 Filter → Interceptor → @BizAuth → DataScope → Service 的完整鉴权链路。
 *
 * 测试场景:
 * 1. 公开端点 (登录) 无需认证即可访问
 * 2. 受保护端点无 Session 时返回 401
 * 3. 受保护的管理端点无 Session 时返回 401
 * 4. 公开字典 API (governance) 可被正常调用
 * 5. 数据库数据验证所有模块表已正确初始化
 *
 * 注意: 排除 Flowable 自动配置，聚焦鉴权链路验证。
 */
@SpringBootTest(
        classes = com.bank.branch.platform.BranchPlatformApplication.class,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "flowable.enabled=false",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration," +
                        "org.flowable.spring.boot.ProcessEngineAutoConfiguration," +
                        "org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration," +
                        "org.flowable.spring.boot.app.AppEngineAutoConfiguration," +
                        "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration," +
                        "org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration," +
                        "org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration," +
                        "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration," +
                        "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration," +
                        "org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration," +
                        "org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration," +
                        "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration," +
                        "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration," +
                        "org.flowable.spring.boot.RestApiAutoConfiguration," +
                        "org.flowable.spring.boot.FlowableJpaAutoConfiguration," +
                        "org.flowable.spring.boot.FlowableSecurityAutoConfiguration," +
                        "org.flowable.spring.boot.EndpointAutoConfiguration," +
                        "org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration"
        }
)
@ActiveProfiles("test")
@Import(TestMockConfig.class)
@AutoConfigureMockMvc
class FullAuthChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ========== 场景 1: 公开端点无需认证 ==========

    @Test
    @DisplayName("鉴权链路 - 公开端点登录无需认证可直接访问")
    void publicEndpoint_login_noAuthRequired() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test\"}"))
                // 登录会执行完整链路:密码校验 → 账户状态检查 → 建立 Session
                .andExpect(status().isOk());
    }

    // ========== 场景 2: 受保护端点无 Session 返回 401 ==========

    @Test
    @DisplayName("鉴权链路 - 无 Session 访问 /api/** 返回 401")
    void protectedEndpoint_noSession_returns401() throws Exception {
        mockMvc.perform(get("/api/orgs/tree"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("鉴权链路 - 无 Session 访问受保护的字典管理端点返回 401")
    void protectedEndpoint_noSession_dictAdmin_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/sys/dicts"))
                .andExpect(status().isUnauthorized());
    }

    // ========== 场景 3: 公开字典 API 可被正常调用 ==========

    @Test
    @DisplayName("鉴权链路 - 公开字典 API (governance) 可被正常调用")
    void crossModuleApi_publicDictApi_callable() throws Exception {
        // 字典 API 是公开的（无需 @BizAuth），直接访问可获取数据
        mockMvc.perform(get("/api/sys/dicts/INDUSTRY/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("鉴权链路 - 字典查询返回有效数据说明治理中心正常工作")
    void governanceDict_query_returnsValidData() throws Exception {
        mockMvc.perform(get("/api/sys/dicts/STATUS/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ========== 场景 4: 数据库验证所有模块表数据正常 ==========

    @Test
    @DisplayName("数据库验证 - workflow 模块 biz_process_map 数据存在")
    void workflowData_exists() {
        Long mapCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_process_map", Long.class);
        assertThat(mapCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("数据库验证 - auth 模块 PT_USER 数据存在")
    void authData_exists() {
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PT_USER", Long.class);
        assertThat(userCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("数据库验证 - governance 模块 sys_dict 数据存在")
    void governanceData_exists() {
        Long dictCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_dict", Long.class);
        assertThat(dictCount).isGreaterThanOrEqualTo(4);
    }
}
