package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.LeadE2ETestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 (b) — lead_approve_v1 真 BPMN 端到端 IT。
 *
 * <p>覆盖完整链路（无 stub / 无 mock 链路对象）：</p>
 * <pre>
 *   APPROVED 分支：
 *     POST /api/leads (create draft)
 *       → cust_lead.lead_status = DRAFT
 *     POST /api/leads/{id}/submit
 *       → LeadService.submitForApproval → workflowApi.startProcess
 *       → Flowable runtimeService.startProcessInstanceByKey("lead_approve_v1")
 *       → cust_lead.lead_status = IN_APPROVAL
 *       → 候选人 ROLE:R_LEAD_BRANCH_MGR
 *     POST /api/workflow/tasks/{taskId}/claim (审批人签收)
 *     POST /api/workflow/tasks/{taskId}/approve (approved=true)
 *       → ProcessCompletedListener.notify → publishEvent(ProcessCompletedEvent, outcome=APPROVED)
 *       → WorkflowCallbackListener.onProcessCompleted (REQUIRES_NEW + AFTER_COMMIT)
 *       → handleApproved → publishEvent(LeadApprovedEvent)
 *       → LeadApprovedListener.handle (AFTER_COMMIT)
 *       → CustMasterAssemblerService.assembleFromLead → masterMapper.insert
 *     断言：cust_lead.lead_status == APPROVED + cust_master 新增 1 条
 *
 *   REJECTED 分支（依赖 Phase 2 (c) 修复）：
 *     同上前 4 步，第 4 步改为 reject (approved=false)
 *       → ProcessCompletedListener.notify → outcome=REJECTED
 *       → WorkflowCallbackListener.handleRejected → 仅推进 lead.status → 不发 LeadApprovedEvent
 *     断言：cust_lead.lead_status == REJECTED + cust_master 计数为 0
 * </pre>
 *
 * <p><strong>触发约定</strong>：</p>
 * <ul>
 *   <li>CI 默认跳过：{@code @EnabledIfSystemProperty(name="lead.e2e.enabled", matches="true")}</li>
 *   <li>本地触发：{@code mvn verify -pl bootstrap -Dlead.e2e.enabled=true -Dit.test=LeadWorkflowE2EIT}</li>
 * </ul>
 *
 * <p>命名 *IT.java 走 maven-failsafe-plugin（{@code mvn verify} 阶段），与 surefire 单元测试隔离。</p>
 */
@SpringBootTest
@ActiveProfiles("lead-e2e")
@Import(LeadE2ETestConfig.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "lead.e2e.enabled", matches = "true")
class LeadWorkflowE2EIT {

    /** 发起人（线索创建者）— 见 lead-e2e-data.sql 中 LE10001 / R_LEAD_RM */
    private static final String INITIATOR_USERNAME = "rm_li";
    /** 审批人（分行经理）— 见 lead-e2e-data.sql 中 LE20001 / R_LEAD_BRANCH_MGR */
    private static final String APPROVER_USERNAME = "branch_mgr_qian";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("lead_approve_v1 APPROVED 分支应让 cust_master 创建")
    void leadApproveWorkflow_approvedBranch_shouldCreateCustomerMaster() throws Exception {
        // ========== 0. 部署校验：lead_approve_v1 必须通过启动链完成部署 ==========
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("lead_approve_v1")
                .latestVersion()
                .singleResult();
        assertThat(definition).as("lead_approve_v1 必须通过正式启动链完成部署").isNotNull();

        MockHttpSession initiatorSession = login(INITIATOR_USERNAME);
        MockHttpSession approverSession = login(APPROVER_USERNAME);

        // ========== 1. 发起人创建线索草稿（POST /api/leads） ==========
        String custName = "Phase2b-Approved-客户-" + uuid8();
        String leadId = createLeadDraft(initiatorSession, custName);
        assertLeadStatus(leadId, "DRAFT");

        // ========== 2. 发起人提交审批（POST /api/leads/{id}/submit） ==========
        submitLead(initiatorSession, leadId);
        assertLeadStatus(leadId, "IN_APPROVAL");

        // 校验前置：cust_master 在审批前不应该有该 leadId 关联记录
        long preCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(preCount).isZero();

        // ========== 3. 审批人查待办，拿到任务 ID ==========
        String taskId = queryFirstTodoTaskId(approverSession);
        assertThat(taskId).as("审批人应有待办任务").isNotBlank();

        // ========== 4. 审批人签收 + 通过 ==========
        claim(approverSession, taskId);
        approve(approverSession, taskId, "Phase2b APPROVED 分支测试通过", Map.of("opinion", "OK"));

        // ========== 5. 断言 1：cust_lead.lead_status == APPROVED ==========
        // ProcessCompletedListener 触发 → WorkflowCallbackListener.handleApproved
        //   → leadMapper.updateStatusById(leadId, "APPROVED")
        //   → publishEvent(LeadApprovedEvent)
        //   → LeadApprovedListener.handle → CustMasterAssemblerService.assembleFromLead
        // 因 WorkflowCallbackListener 用 REQUIRES_NEW + AFTER_COMMIT，approve 返回时整链已同步落库
        assertLeadStatus(leadId, "APPROVED");

        // ========== 6. 断言 2：cust_master 表新增 1 条记录 ==========
        long postCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(postCount)
                .as("APPROVED 分支应触发 LeadApprovedListener → cust_master 创建 1 条")
                .isEqualTo(1L);

        // 同时校验生成的 cust_master 关键字段
        // FU-23：用 RowMapper 显式 rs.getInt("deleted") 替代 (Number) cast，
        // 避免 MySQL 驱动默认 tinyInt1isBit=true 把 TINYINT(1) 当 Boolean 导致 ClassCastException，
        // 让 application-lead-e2e.yml 不再需要 url 加 tinyInt1isBit=false（与 default/flowable-e2e profile 一致）。
        Map<String, Object> created = jdbcTemplate.queryForObject(
                "SELECT cust_name, status, deleted FROM CUST_MASTER WHERE lead_id = ?",
                (rs, rowNum) -> Map.of(
                        "cust_name", rs.getString("cust_name"),
                        "status", rs.getString("status"),
                        "deleted", rs.getInt("deleted")
                ),
                leadId
        );
        assertThat(created.get("cust_name")).isEqualTo(custName);
        assertThat(created.get("status")).isEqualTo("ACTIVE");
        assertThat((Integer) created.get("deleted")).isZero();
    }

    @Test
    @DisplayName("lead_approve_v1 REJECTED 分支应让 cust_master 不创建")
    void leadApproveWorkflow_rejectedBranch_shouldNotCreateCustomerMaster() throws Exception {
        // 部署前提（与 APPROVED 用例共享，但单测独立运行也要校验）
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("lead_approve_v1")
                .latestVersion()
                .singleResult();
        assertThat(definition).isNotNull();

        MockHttpSession initiatorSession = login(INITIATOR_USERNAME);
        MockHttpSession approverSession = login(APPROVER_USERNAME);

        // 1. 创建草稿
        String custName = "Phase2b-Rejected-客户-" + uuid8();
        String leadId = createLeadDraft(initiatorSession, custName);

        // 2. 提交审批
        submitLead(initiatorSession, leadId);
        assertLeadStatus(leadId, "IN_APPROVAL");

        // FU-8（2026-04-29 补对称校验）：与 APPROVED case 对称，先确认 cust_master 在驳回前不应有该 leadId 关联记录
        long preCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(preCount).isZero();

        // 3. 审批人签收 + 驳回
        String taskId = queryFirstTodoTaskId(approverSession);
        claim(approverSession, taskId);
        reject(approverSession, taskId, "Phase2b REJECTED 分支测试驳回");

        // 4. 断言 1：cust_lead.lead_status == REJECTED（依赖 Phase 2 (c) 修复后才生效）
        assertLeadStatus(leadId, "REJECTED");

        // 5. 断言 2：cust_master 表对应该 leadId 不应有任何记录
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(count)
                .as("REJECTED 分支不应触发 LeadApprovedListener，cust_master 必须为 0 条")
                .isZero();
    }

    // ============================== 私有 helper ==============================

    /** 登录并取回 MockHttpSession，沿用 FlowableWorkflowCenterE2ETest 同样模式。 */
    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /**
     * POST /api/leads 创建草稿，返回新 leadId。
     */
    private String createLeadDraft(MockHttpSession session, String custName) throws Exception {
        // Map.of 最多 10 对，超出用 Map.ofEntries
        String body = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("custName", custName),
                Map.entry("unifiedCreditCode", "91310000PHASE2BTEST"),
                Map.entry("contactPerson", "测试联系人"),
                Map.entry("contactMobile", "13800000000"),
                Map.entry("industry", "IT"),
                Map.entry("groupType", "GROUP"),
                Map.entry("customerType", "ENTERPRISE"),
                Map.entry("isKeystone", 1),
                Map.entry("enterpriseType", "PRIVATE"),
                Map.entry("groupName", "Phase2b 测试集团"),
                Map.entry("isAccountOpened", 0),
                Map.entry("customerDesc", "Phase2b BPMN E2E IT"),
                Map.entry("leadSource", "MANUAL")
        ));

        MvcResult result = mockMvc.perform(post("/api/leads")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String leadId = root.path("data").asText();
        assertThat(leadId).as("createDraft 应返回 leadId").isNotBlank();
        return leadId;
    }

    /** POST /api/leads/{id}/submit 提交审批。 */
    private void submitLead(MockHttpSession session, String leadId) throws Exception {
        mockMvc.perform(post("/api/leads/{id}/submit", leadId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /** GET /api/workflow/tasks 取第一条 todo 的 taskId。 */
    private String queryFirstTodoTaskId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/workflow/tasks").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode page = root.path("page");
        assertThat(page.path("total").asLong()).as("待办列表应非空").isPositive();
        return page.path("records").get(0).path("taskId").asText();
    }

    /** POST /api/workflow/tasks/{id}/claim 签收。 */
    private void claim(MockHttpSession session, String taskId) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/claim", taskId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /** POST /api/workflow/tasks/{id}/approve 通过。 */
    private void approve(MockHttpSession session, String taskId, String opinion,
                         Map<String, Object> formData) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", taskId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "opinion", opinion,
                                "formData", formData
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /** POST /api/workflow/tasks/{id}/reject 驳回。 */
    private void reject(MockHttpSession session, String taskId, String opinion) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/reject", taskId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("opinion", opinion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /** 直接查 cust_lead.lead_status 校验。 */
    private void assertLeadStatus(String leadId, String expectedStatus) {
        String actual = jdbcTemplate.queryForObject(
                "SELECT lead_status FROM CUST_LEAD WHERE id = ?",
                String.class,
                leadId
        );
        assertThat(actual)
                .as("cust_lead.lead_status (leadId=%s)", leadId)
                .isEqualTo(expectedStatus);
    }

    /** 生成 8 位随机后缀，避免单 JVM 多 case 之间冲突。 */
    private String uuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
