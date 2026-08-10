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
 * <p><strong>2026-08-10 无角色切换回归</strong>：类级 SQL 创建三个临时用户：仅报送员、
 * 仅支部审核员，以及同时拥有两个角色的并集用户。并集用户故意把支部审核员设为默认角色，
 * 但仍必须同时具备报送和审核能力；测试全程不调用 {@code /api/auth/switch-role}。
 * 这既验证资源授权取全部有效角色并集，也保留报送员无审核/导出、支部审核员无高危执行权的负例；
 * 即使并集用户同时具备上报和审核资源，Service 仍必须基于 RE_SUBMIT.submitterId 二次拒绝自审。</p>
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
        MockHttpSession session = login("re_it_report", "password");

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
        MockHttpSession session = login("re_it_report", "password");

        mockMvc.perform(get("/api/re/export/submit").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));
    }

    @Test
    @DisplayName("红色引擎鉴权 - R_RE_BRREV(党建支部审核员)调执行逾期扣分端点返回 403(支部审核无 EXECUTE 权)")
    void cockpitOverdueExecute_brrevRole_returns403() throws Exception {
        MockHttpSession session = login("re_it_brrev", "password");

        mockMvc.perform(post("/api/re/cockpit/overdue/execute")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submitId\":999999,\"deductionPoints\":5,\"reason\":\"RBAC 403 回归\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));
    }

    @Test
    @DisplayName("红色引擎鉴权 - 仅报送员无需切换角色即可创建上报")
    void createSubmit_reportRole_withoutSwitch_returns200() throws Exception {
        MockHttpSession session = login("re_it_report", "password");

        mockMvc.perform(post("/api/re/submits")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitJson("仅报送角色")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("红色引擎鉴权 - 报送员+支部审核员取权限并集，无需切换即可上报并查看待审队列")
    void submitAndReview_multiRoleUnion_withoutSwitch_returns200() throws Exception {
        MockHttpSession session = login("re_it_union", "password");

        mockMvc.perform(post("/api/re/submits")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitJson("多角色权限并集")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(get("/api/re/reviews/queue")
                        .session(session)
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("红色引擎鉴权 - 报送员+支部审核员并集不包含导出权限")
    void export_multiRoleUnionWithoutExportPermission_returns403() throws Exception {
        MockHttpSession session = login("re_it_union", "password");

        mockMvc.perform(get("/api/re/export/submit").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));
    }

    @Test
    @DisplayName("红色引擎鉴权 - 报送员+支部审核员不能审核本人创建的上报")
    void approveAndReject_ownSubmitWithMultiRoleUnion_returnsRe40008() throws Exception {
        MockHttpSession session = login("re_it_union", "password");

        MvcResult createResult = mockMvc.perform(post("/api/re/submits")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitJson("多角色禁止自审")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        long submitId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").asLong();

        mockMvc.perform(post("/api/re/reviews/{id}/approve", submitId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5,\"feedback\":\"禁止自审回归\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RE-40008"))
                .andExpect(jsonPath("$.message").value("禁止审核本人提交的记录"));

        mockMvc.perform(post("/api/re/reviews/{id}/reject", submitId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"禁止自审回归\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RE-40008"))
                .andExpect(jsonPath("$.message").value("禁止审核本人提交的记录"));

        mockMvc.perform(get("/api/re/reviews/{id}/preview", submitId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value(1));
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

    /** 构造不含附件的合法上报请求，避免测试触发外部对象存储。 */
    private String validSubmitJson(String projectName) {
        return """
            {
              "dimension":"dim1",
              "itemCode":"1.1",
              "itemName":"红色引擎鉴权回归",
              "maxScore":10,
              "projectName":"%s",
              "submitType":1,
              "submitDate":"2026-08-10",
              "formData":"{}",
              "fileObjectIds":[]
            }
            """.formatted(projectName);
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
