package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.FlowableRealEnvTestConfig;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * workflow-center 在真实 MySQL + Redis 开发环境下的联调验证。
 */
@SpringBootTest
@ActiveProfiles("flowable-real-env")
@Import(FlowableRealEnvTestConfig.class)
@AutoConfigureMockMvc
class FlowableWorkflowCenterRealEnvTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${spring.session.redis.namespace}")
    private String sessionNamespace;

    @BeforeEach
    @AfterEach
    void cleanupRedisState() {
        Set<String> keys = stringRedisTemplate.keys(sessionNamespace + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        Set<String> authKeys = stringRedisTemplate.keys("auth:*");
        if (authKeys != null && !authKeys.isEmpty()) {
            stringRedisTemplate.delete(authKeys);
        }
    }

    @Test
    @DisplayName("loan_approve_v1 应在真实 MySQL 与 Redis 下通过登录、待办和审批链验证")
    void loanApproveWorkflow_shouldPassRealEnvironmentValidation() throws Exception {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("loan_approve_v1")
                .latestVersion()
                .singleResult();
        assertThat(definition).as("loan_approve_v1 必须通过正式启动链完成部署").isNotNull();

        mockMvc.perform(get("/api/workflow/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-40105"));

        SessionHandle rmSession = login("rm_zhang");
        SessionHandle branchSession = login("branch_wang");
        SessionHandle corpSession = login("corp_zhao");
        SessionHandle reviewerSession = login("reviewer_chen");
        SessionHandle approverSession = login("approver_he");
        SessionHandle noWorkflowSession = login("no_workflow_user");

        assertRedisBackedSession(rmSession);

        mockMvc.perform(get("/api/workflow/tasks").cookie(noWorkflowSession.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));

        String passBizId = "REAL" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WorkflowLaunchResp passLaunch = submitProcess(rmSession, passBizId, "真实环境审批通过链");
        assertThat(passLaunch.getProcessInstanceId()).isNotBlank();
        assertThat(passLaunch.getFirstTaskId()).isNotBlank();
        assertActiveNode(passLaunch.getProcessInstanceId(), "branch_approve", null);
        assertProcessStatus(passLaunch.getProcessInstanceId(), "RUNNING");

        JsonNode branchTodo = queryFirstTodo(branchSession);
        assertThat(branchTodo.path("taskId").asText()).isEqualTo(passLaunch.getFirstTaskId());
        assertThat(branchTodo.path("claimable").asBoolean()).isTrue();
        assertThat(queryTodoCount(corpSession)).isZero();

        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", passLaunch.getFirstTaskId())
                        .cookie(corpSession.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinion\":\"非法审批\",\"formData\":{\"amount\":100}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40903"));

        claim(branchSession, passLaunch.getFirstTaskId());
        assertActiveNode(passLaunch.getProcessInstanceId(), "branch_approve", "E20001");
        assertCurrentAssignee(passLaunch.getProcessInstanceId(), "E20001");

        transfer(branchSession, passLaunch.getFirstTaskId(), "E10001", "经营机构负责人转发给客户经理补录材料");
        assertActiveNode(passLaunch.getProcessInstanceId(), "branch_approve", "E10001");
        assertCurrentAssignee(passLaunch.getProcessInstanceId(), "E10001");
        JsonNode rmTodo = queryFirstTodo(rmSession);
        assertThat(rmTodo.path("taskId").asText()).isEqualTo(passLaunch.getFirstTaskId());

        approve(rmSession, passLaunch.getFirstTaskId(), "客户经理补录后提交通过", Map.of("amount", 1000000, "needCreditMeeting", "NO"));
        String corpTaskId = queryFirstTodo(corpSession).path("taskId").asText();
        assertActiveNode(passLaunch.getProcessInstanceId(), "corp_review", null);

        claim(corpSession, corpTaskId);
        approve(corpSession, corpTaskId, "公司部同意", Map.of("corpOpinion", "同意"));
        String reviewTaskId = queryFirstTodo(reviewerSession).path("taskId").asText();
        assertActiveNode(passLaunch.getProcessInstanceId(), "credit_check", null);

        claim(reviewerSession, reviewTaskId);
        approve(reviewerSession, reviewTaskId, "授信审查通过", Map.of("reviewOpinion", "通过"));
        String approvalTaskId = queryFirstTodo(approverSession).path("taskId").asText();
        assertActiveNode(passLaunch.getProcessInstanceId(), "credit_approval", null);

        claim(approverSession, approvalTaskId);
        approve(approverSession, approvalTaskId, "授信批复通过", Map.of("approvalLimit", 1000000));

        assertNoRuntimeTask(passLaunch.getProcessInstanceId());
        assertProcessStatus(passLaunch.getProcessInstanceId(), "COMPLETED");
        assertTaskHistory(passLaunch.getProcessInstanceId(), List.of(
                "branch_approve", "corp_review", "credit_check", "credit_approval"
        ));
        assertThat(loadComments(passLaunch.getProcessInstanceId())).hasSize(5);

        String cancelBizId = "CAN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WorkflowLaunchResp cancelLaunch = submitProcess(rmSession, cancelBizId, "真实环境发起人撤回链");
        assertActiveNode(cancelLaunch.getProcessInstanceId(), "branch_approve", null);

        cancelProcess(rmSession, cancelLaunch.getProcessInstanceId(), "发起人主动撤回");
        assertNoRuntimeTask(cancelLaunch.getProcessInstanceId());
        assertProcessStatus(cancelLaunch.getProcessInstanceId(), "CANCELLED");
    }

    private SessionHandle login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = root.path("data").path("token").asText();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionId).isNotBlank();
        assertThat(sessionCookie).isNotNull();
        return new SessionHandle(sessionId, sessionCookie);
    }

    private void assertRedisBackedSession(SessionHandle session) {
        String sessionKey = sessionNamespace + ":sessions:" + session.sessionId();
        assertThat(stringRedisTemplate.hasKey(sessionKey))
                .as("登录成功后应写入 Redis Session")
                .isTrue();
    }

    private WorkflowLaunchResp submitProcess(SessionHandle session, String bizId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workflow/processes/submit")
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bizType", "LOAN",
                                "bizId", bizId,
                                "businessKey", "LOAN:" + bizId,
                                "processDefinitionKey", "loan_approve_v1",
                                "title", title,
                                "variables", Map.of("bizId", bizId, "title", title)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.path("data");
        return new WorkflowLaunchResp(
                data.path("processInstanceId").asText(),
                data.path("businessKey").asText(),
                data.path("firstTaskId").asText()
        );
    }

    private long queryTodoCount(SessionHandle session) throws Exception {
        JsonNode page = queryTodoPage(session);
        return page.path("total").asLong();
    }

    private JsonNode queryFirstTodo(SessionHandle session) throws Exception {
        JsonNode page = queryTodoPage(session);
        assertThat(page.path("total").asLong()).isPositive();
        return page.path("records").get(0);
    }

    private JsonNode queryTodoPage(SessionHandle session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/workflow/tasks").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("page");
    }

    private void claim(SessionHandle session, String taskId) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/claim", taskId).cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void approve(SessionHandle session, String taskId, String opinion, Map<String, Object> formData) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", taskId)
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "opinion", opinion,
                                "formData", formData
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void transfer(SessionHandle session, String taskId, String targetEmpId, String reason) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/transfer", taskId)
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetEmpId", targetEmpId,
                                "reason", reason
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void reject(SessionHandle session, String taskId, String opinion) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/reject", taskId)
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("opinion", opinion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void cancelProcess(SessionHandle session, String processInstanceId, String reason) throws Exception {
        mockMvc.perform(post("/api/workflow/processes/{processInstanceId}/cancel", processInstanceId)
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void assertProcessStatus(String processInstanceId, String expectedStatus) {
        String sql = "SELECT process_status FROM biz_process_map WHERE process_instance_id = ?";
        assertThat(jdbcTemplate.queryForObject(sql, String.class, processInstanceId)).isEqualTo(expectedStatus);
    }

    private void assertCurrentAssignee(String processInstanceId, String expectedAssignee) {
        String sql = "SELECT current_assignee FROM biz_process_map WHERE process_instance_id = ?";
        assertThat(jdbcTemplate.queryForObject(sql, String.class, processInstanceId)).isEqualTo(expectedAssignee);
    }

    private void assertActiveNode(String processInstanceId, String expectedNodeKey, String expectedAssignee) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT TASK_DEF_KEY_, ASSIGNEE_ FROM ACT_RU_TASK WHERE PROC_INST_ID_ = ?",
                processInstanceId
        );
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("TASK_DEF_KEY_")).isEqualTo(expectedNodeKey);
        assertThat(rows.get(0).get("ASSIGNEE_")).isEqualTo(expectedAssignee);
    }

    private void assertNoRuntimeTask(String processInstanceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ACT_RU_TASK WHERE PROC_INST_ID_ = ?",
                Integer.class,
                processInstanceId
        );
        assertThat(count).isZero();
    }

    private void assertTaskHistory(String processInstanceId, List<String> expectedNodeKeys) {
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT TASK_DEF_KEY_ FROM ACT_HI_TASKINST WHERE PROC_INST_ID_ = ? ORDER BY START_TIME_ ASC",
                String.class,
                processInstanceId
        );
        assertThat(keys).containsExactlyElementsOf(expectedNodeKeys);
    }

    private List<String> loadComments(String processInstanceId) {
        return jdbcTemplate.queryForList(
                "SELECT MESSAGE_ FROM ACT_HI_COMMENT WHERE PROC_INST_ID_ = ? ORDER BY TIME_ ASC",
                String.class,
                processInstanceId
        );
    }

    private record SessionHandle(String sessionId, Cookie cookie) {
    }
}
