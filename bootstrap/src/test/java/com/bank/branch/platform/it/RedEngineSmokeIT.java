package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 12 — 红色引擎（red-engine-center）鉴权链路集成冒烟 IT。
 *
 * <p>验证 {@code GET /api/re/orgs/tree} 真实链路（Filter → Interceptor → @BizAuth → Service）：</p>
 * <ul>
 *   <li>未登录访问 → 401</li>
 *   <li>admin 登录（主角色 SYS_ADMIN，RBAC 跳过） → 200，返回党组织树，总节点数 = 10（RE_PARTY_ORG 种子行数）</li>
 *   <li>已登录但当前角色未绑定任何 {@code P_RE_*} 资源 → 403</li>
 * </ul>
 *
 * <p><strong>为什么不用 {@code @ActiveProfiles("test")}（与 {@link PortalWorkspaceMetricIT} 等多数 IT 不同）</strong>：
 * {@code auth-permission-center} 的 {@code WebMvcAuthConfig} 标注 {@code @Profile("!test")}——这是全仓库
 * 唯一注册生产 {@code AuthenticationFilter}（Session→CurrentUserContext）与
 * {@code AuthorizationInterceptor}（真实 RBAC：ResourceMatcher→RbacAuthorizer→BizMetaResolver→BizScopeApi）
 * 的地方。字面量 {@code "test"} profile 下该 Configuration 整体不加载，此时
 * {@code TestSecurityConfig} 的替代 Filter 只检查 session 是否存在，不做任何 RBAC 校验——这也是
 * {@link PortalWorkspaceMetricIT} 等 IT 手工 {@code session.setAttribute("empId", ...)} 就能通过任意接口的
 * 原因。在该架构下"无 {@code P_RE_*} 权限角色访问返回 403"永远无法被真实触发（任何非空 empId 的
 * session 都会 200），因此本 IT 改用 {@code redengine-smoke} profile（见
 * {@code application-redengine-smoke.yml} 头部注释）——只是换了个不叫 "test" 的 profile 名字（全仓库
 * grep {@code @Profile} 只有这一处，不影响其它生产 Bean 装配），换回真实鉴权链路，登录方式沿用
 * {@link com.bank.branch.platform.it.LeadWorkflowE2EIT}/{@code FlowableWorkflowCenterE2ETest} 已验证过的
 * "真实 POST /api/auth/login 拿 session，同一 test 方法内复用该 session 访问后续接口" 模式（而非
 * {@link PortalWorkspaceMetricIT} 的手工塞 empId 模式——那种模式下 RBAC 拦截器根本没注册，无法用来验证
 * 403）。数据源仍与 "test" profile 一致，指向本地真实 {@code onepl_test_bootstrap}（Task 3/4 红色引擎
 * 种子已在此库：RE_PARTY_ORG 10 行 + 17 条 P_RE_* PT_RESOURCE）。</p>
 *
 * <p><strong>case③ 账号选择</strong>：{@code tech_wu}（USER_ID=E40002，角色 R_BACK_TECH，密码
 * {@code 123456}，见 {@code docs/CLAUDE.md} "10 个测试账户统一密码 123456" 章节）——已用 SQL 核实其
 * ISENABLED=0(启用)/ISLOCKED=0(未锁)，且唯一角色 R_BACK_TECH 在 PT_ROLE_RESOURCE 里没有任何
 * {@code P_RE_%} 资源绑定。</p>
 *
 * <p><strong>终审 F-2 收口（case④⑤⑥）</strong>：终审报告 {@code .superpowers/sdd/final-review.md}
 * Finding F-2 指出写/导出端点缺 HTTP 层 RBAC 403 自动化用例，本次补齐 3 条：
 * {@code R_RE_REPORT} 调 {@code POST /reviews/{id}/approve}（报送员无审核权）、
 * {@code R_RE_REPORT} 调 {@code GET /export/submit}（报送员无导出权，{@code P_RE_EXPORT} 仅绑
 * {@code R_RE_ORGREV}/{@code R_RE_SECR}/SYS_ADMIN）、{@code R_RE_BRREV} 调
 * {@code POST /cockpit/overdue/execute}（支部审核无 EXECUTE 权，{@code P_RE_CKPT_EXEC} 仅绑
 * {@code R_RE_ORGREV}）。库内核查（2026-07-19）确认 onepl_test_bootstrap 当前无任何账号绑定
 * {@code RE_ROLE_1..4}，故沿用 {@code task-17d-report.md} §五.1 的"临时插 {@code PT_USER_ROLE} 行 +
 * {@code switch-role}"模式，账号改选 {@code reviewer_chen}（USER_ID=E60001，主角色
 * {@code R_CREDIT_REVIEWER}，{@code DEFAULT_ASSIGN=1}）而非 task-17d 的 {@code tech_wu}——
 * 因为 {@code tech_wu} 唯一角色 {@code R_BACK_TECH} 的 {@code DEFAULT_ASSIGN} 实际为 0，
 * 若给它追加党建角色行，存在登录后主角色被"回退到结果集首个元素"逻辑意外漂移到新角色、
 * 反而污染 case③ 断言的风险（详见 {@code redengine-rbac-403-temp-roles.sql} 头部注释）；
 * {@code reviewer_chen} 主角色 {@code DEFAULT_ASSIGN=1} 明确存在，{@code AuthService}
 * 按 {@code DEFAULT_ASSIGN=1} 直查主角色，追加的 {@code DEFAULT_ASSIGN=0} 临时行零副作用。
 * 类级 {@code @Sql} 在每个测试方法前后插入/删除该临时绑定，仅作用于 onepl_test_bootstrap，
 * 不留残留。<strong>密码坑</strong>：{@code reviewer_chen} 登录密码为 {@code password}，
 * 并非文档惯例 {@code 123456}——其 {@code PWD} 哈希与 bootstrap {@code admin} 完全相同
 * （{@code 2026-04-10-pt-align-and-test-seed.sql} 头部"BCrypt('123456')"注释系历史误标，
 * {@code lead-e2e-data.sql} 已注明该哈希实际明文为 {@code password}，本类 case②
 * {@code login("admin","password")} 亦为佐证），实测已核实通过。</p>
 */
@SpringBootTest
@ActiveProfiles("redengine-smoke")
@Import(TestMockConfig.class)
@AutoConfigureMockMvc
@Sql(scripts = "/it/redengine-rbac-403-temp-roles.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/it/redengine-rbac-403-temp-roles-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
class RedEngineSmokeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("红色引擎鉴权 - 未登录访问党组织树返回 401")
    void orgTree_noSession_returns401() throws Exception {
        mockMvc.perform(get("/api/re/orgs/tree"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("红色引擎鉴权 - admin 登录（SYS_ADMIN 主角色）访问党组织树返回 200，总节点数=10")
    void orgTree_adminLogin_returns200_withTenNodes() throws Exception {
        MockHttpSession session = login("admin", "password");

        MvcResult result = mockMvc.perform(get("/api/re/orgs/tree").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        int totalNodes = countTreeNodes(data);
        // RE_PARTY_ORG 种子共 10 行（1 个 level=1 根节点 + 9 个 level=2 子节点），
        // getOrgTree 按 parentId 递归组树后，树结构下的总节点数应等于种子总行数 10
        assertThat(totalNodes).as("党组织树总节点数应等于 RE_PARTY_ORG 种子行数").isEqualTo(10);
    }

    @Test
    @DisplayName("红色引擎鉴权 - 无 P_RE_* 资源绑定角色(tech_wu/R_BACK_TECH)访问党组织树返回 403")
    void orgTree_roleWithoutRedEnginePermission_returns403() throws Exception {
        MockHttpSession session = login("tech_wu", "123456");

        mockMvc.perform(get("/api/re/orgs/tree").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("红色引擎鉴权 - R_RE_REPORT(党建报送员)调审核通过端点返回 403(报送员无审核权)")
    void reviewApprove_reportRole_returns403() throws Exception {
        MockHttpSession session = login("reviewer_chen", "password");
        switchRole(session, "RE_ROLE_4"); // R_RE_REPORT，P_RE_REVIEW_APPR 仅绑 R_RE_BRREV/R_RE_ORGREV

        mockMvc.perform(post("/api/re/reviews/999999/approve")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5,\"feedback\":\"RBAC 403 回归\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));
    }

    @Test
    @DisplayName("红色引擎鉴权 - R_RE_REPORT(党建报送员)调数据导出端点返回 403(报送员无导出权)")
    void export_reportRole_returns403() throws Exception {
        MockHttpSession session = login("reviewer_chen", "password");
        switchRole(session, "RE_ROLE_4"); // R_RE_REPORT，P_RE_EXPORT 仅绑 R_RE_ORGREV/R_RE_SECR

        mockMvc.perform(get("/api/re/export/submit").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));
    }

    @Test
    @DisplayName("红色引擎鉴权 - R_RE_BRREV(党建支部审核员)调执行逾期扣分端点返回 403(支部审核无 EXECUTE 权)")
    void cockpitOverdueExecute_brrevRole_returns403() throws Exception {
        MockHttpSession session = login("reviewer_chen", "password");
        switchRole(session, "RE_ROLE_2"); // R_RE_BRREV，P_RE_CKPT_EXEC 仅绑 R_RE_ORGREV

        mockMvc.perform(post("/api/re/cockpit/overdue/execute")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submitId\":999999,\"deductionPoints\":5,\"reason\":\"RBAC 403 回归\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));
    }

    // ============================== 私有 helper ==============================

    /**
     * 真实 POST /api/auth/login 登录并取回 MockHttpSession，沿用
     * {@link com.bank.branch.platform.it.LeadWorkflowE2EIT#login} 同款模式。
     */
    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /**
     * 真实 POST /api/auth/switch-role 切换会话当前激活角色（仅本次会话生效，不落库），
     * 沿用 {@code task-17d-report.md} §五.1 验证过的"会话内单激活角色"切换模式——
     * 平台登录只取 {@code DEFAULT_ASSIGN=1} 的角色作为 session 激活角色，测试账号临时追加的
     * 党建角色（{@code DEFAULT_ASSIGN=0}）必须经此接口显式切换才会进入鉴权判定集合。
     */
    private void switchRole(MockHttpSession session, String roleId) throws Exception {
        mockMvc.perform(post("/api/auth/switch-role")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + roleId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /** 递归统计树节点总数（含根节点自身 + 所有层级 children）。 */
    private int countTreeNodes(JsonNode nodes) {
        int count = 0;
        for (JsonNode node : nodes) {
            count += 1 + countTreeNodes(node.path("children"));
        }
        return count;
    }
}
