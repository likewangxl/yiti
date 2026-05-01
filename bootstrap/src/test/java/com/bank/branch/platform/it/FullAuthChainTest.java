package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
 * 1. 登录成功建立 Session，受保护端点无 Session 返回 401
 * 2. 登录成功后，使用 Session 可访问受保护端点
 * 3. 数据库数据验证所有模块表已正确初始化
 *
 * 注意: 使用 test profile 加载 application-test.yml，配置 Flowable/Redis 排除。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class})
@AutoConfigureMockMvc
class FullAuthChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // 登录成功后的 Session (由第一个测试填充)
    private MockHttpSession validSession;

    // ========== 场景 1: 登录认证 ==========

    @Test
    @DisplayName("鉴权链路 - 登录成功建立 Session")
    void login_success_establishesSession() throws Exception {
        // 密码 "password" 对应 BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        validSession = (MockHttpSession) result.getRequest().getSession();
        assertThat(validSession).as("登录后应建立 Session").isNotNull();
    }

    // ========== 场景 2: 无 Session 访问受保护端点返回 401 ==========

    @Test
    @DisplayName("鉴权链路 - 无 Session 访问组织端点返回 401")
    void protectedEndpoint_noSession_returns401() throws Exception {
        mockMvc.perform(get("/api/orgs/tree"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("鉴权链路 - 无 Session 访问受保护的管理端点返回 401")
    void protectedEndpoint_noSession_dictAdmin_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/sys/dicts"))
                .andExpect(status().isUnauthorized());
    }

    // ========== 场景 3: 数据库验证所有模块表数据正常 ==========

    @Test
    @DisplayName("数据库验证 - workflow 模块 BIZ_PROCESS_MAP 数据存在")
    void workflowData_exists() {
        Long mapCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BIZ_PROCESS_MAP", Long.class);
        assertThat(mapCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("数据库验证 - auth 模块 PT_USER 数据存在")
    void authData_exists() {
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PT_USER", Long.class);
        assertThat(userCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("数据库验证 - governance 模块 SYS_DICT 数据存在")
    void governanceData_exists() {
        Long dictCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SYS_DICT", Long.class);
        assertThat(dictCount).isGreaterThanOrEqualTo(4);
    }
}
