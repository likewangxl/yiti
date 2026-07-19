package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/perf/metric-calc/trigger} 匿名白名单摘除的鉴权回归 IT。
 *
 * <p>背景：2026-05-27（{@code 326bba98}）该路径被同时加入 {@code AuthenticationFilter.WHITELIST}
 * 与 {@code WebMvcAuthConfig} 排除清单用于调试期裸调，导致任何人无需登录即可触发指标批量计算，
 * Controller 上的 {@code @BizAuth} 完全不生效。2026-07-19 全仓排查确认无任何前端/脚本/网关裸调方
 * （唯一命中即白名单本身），资源 {@code P_PERF_CALC_TRIG} 与 18 条角色绑定在生产基线齐备，
 * 授权用户经会话调用不受影响，故摘除两处白名单，恢复 Session + RBAC + {@code @BizAuth} 全链路。
 *
 * <p>profile 选用 {@code redengine-smoke} 的原因与 {@link RedEngineSmokeIT} 相同：字面量
 * {@code "test"} profile 下 {@code WebMvcAuthConfig}（@Profile("!test")）整体不装配，
 * 生产鉴权链在测试上下文中根本不存在，无法验证 401。
 *
 * <p>两个用例均<strong>不带 level 参数</strong>，即便鉴权层放行也会在 MVC 参数绑定处止步（400），
 * 绝不会真正触发批量计算，测试库 {@code onepl_test_bootstrap} 零副作用。
 *
 * <p>前置夹具（{@code /it/perf-trigger-auth-resource.sql}）：测试库 2026-04 基线早于该资源
 * 2026-05-27 的登记时间，缺 {@code P_PERF_CALC_TRIG} 行，{@code ResourceMatcher} 查无匹配
 * 会按 Fail Close 直接 403，admin 用例无从验证；故每个用例前临时插入资源行 + {@code R_ADMIN}
 * 绑定（对齐生产基线 {@code seed-yiti-prod-golive.sql} 同行字段），用例后精确清理，无残留。
 */
@SpringBootTest
@ActiveProfiles("redengine-smoke")
@Import(TestMockConfig.class)
@AutoConfigureMockMvc
@Sql(scripts = "/it/perf-trigger-auth-resource.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/it/perf-trigger-auth-resource-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
class PerfTriggerAuthWhitelistRemovalIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("指标计算触发 - 未登录访问返回 401（白名单摘除后不得再匿名放行）")
    void trigger_noSession_returns401() throws Exception {
        mockMvc.perform(post("/api/perf/metric-calc/trigger"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("指标计算触发 - admin 登录后到达参数绑定层返回 400（证明授权用户仍可经全链路调用）")
    void trigger_adminSession_reachesMvcBinding_returns400() throws Exception {
        MockHttpSession session = login("admin", "password");

        // 缺少必填 level 参数 → MissingServletRequestParameterException → 400：
        // 既证明请求穿过了 AuthenticationFilter + AuthorizationInterceptor（非 401/403），
        // 又不会真正执行批量计算（零副作用）
        mockMvc.perform(post("/api/perf/metric-calc/trigger").session(session))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
