package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.FlowableE2ETestConfig;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * workflow-center Flowable 真实 E2E 验证。
 */
@SpringBootTest
@ActiveProfiles("flowable-e2e")
@Import(FlowableE2ETestConfig.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "flowable.e2e.enabled", matches = "true")
class FlowableWorkflowCenterE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowApi workflowApi;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("loan_approve_v1 应通过正式部署、真实登录、权限链与通过/驳回链验证")
    void loanApproveWorkflow_shouldPassRealE2EValidation() throws Exception {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("loan_approve_v1")
                .latestVersion()
                .singleResult();
        assertThat(definition).as("loan_approve_v1 必须通过正式启动链完成部署").isNotNull();

        mockMvc.perform(get("/api/workflow/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-40105"));

        MockHttpSession rmSession = login("rm_zhang");
        MockHttpSession branchSession = login("branch_wang");
        MockHttpSession corpSession = login("corp_zhao");
        MockHttpSession reviewerSession = login("reviewer_chen");
        MockHttpSession approverSession = login("approver_he");
        MockHttpSession noWorkflowSession = login("no_workflow_user");

        mockMvc.perform(get("/api/workflow/tasks").session(noWorkflowSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-40301"));

        String passBizId = "LA" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WorkflowLaunchResp passLaunch = workflowApi.startProcess(startCmd(passBizId, "资产投放审批-通过链"));
        assertThat(passLaunch.getProcessInstanceId()).isNotBlank();
        assertThat(passLaunch.getFirstTaskId()).isNotBlank();
        assertActiveNode(passLaunch.getProcessInstanceId(), "branch_approve", null);
        assertProcessStatus(passLaunch.getProcessInstanceId(), "RUNNING");

        JsonNode branchTodo = queryFirstTodo(branchSession);
        assertThat(branchTodo.path("taskId").asText()).isEqualTo(passLaunch.getFirstTaskId());
        assertThat(branchTodo.path("claimable").asBoolean()).isTrue();
        assertThat(branchTodo.path("taskName").asText()).isNotBlank();
        assertThat(queryTodoCount(corpSession)).isZero();

        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", passLaunch.getFirstTaskId())
                        .session(corpSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinion\":\"非法审批\",\"formData\":{\"amount\":100}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-40903"));
        assertActiveNode(passLaunch.getProcessInstanceId(), "branch_approve", null);
        assertCommentCount(passLaunch.getProcessInstanceId(), 0);

        claim(branchSession, passLaunch.getFirstTaskId());
        assertActiveNode(passLaunch.getProcessInstanceId(), "branch_approve", "E20001");
        assertCurrentAssignee(passLaunch.getProcessInstanceId(), "E20001");

        approve(branchSession, passLaunch.getFirstTaskId(), "经营机构负责人同意", Map.of("amount", 1000000, "needCreditMeeting", "NO"));
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
        assertThat(loadComments(passLaunch.getProcessInstanceId())).hasSize(4);

        String rejectBizId = "LB" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WorkflowLaunchResp rejectLaunch = workflowApi.startProcess(startCmd(rejectBizId, "资产投放审批-驳回链"));
        assertActiveNode(rejectLaunch.getProcessInstanceId(), "branch_approve", null);

        String rejectTaskId = queryFirstTodo(branchSession).path("taskId").asText();
        claim(branchSession, rejectTaskId);
        reject(branchSession, rejectTaskId, "经营机构负责人驳回");

        assertNoRuntimeTask(rejectLaunch.getProcessInstanceId());
        assertProcessStatus(rejectLaunch.getProcessInstanceId(), "CANCELLED");
        assertTaskHistory(rejectLaunch.getProcessInstanceId(), List.of("branch_approve"));
        assertThat(loadComments(rejectLaunch.getProcessInstanceId()))
                .singleElement()
                .satisfies(comment -> assertThat(comment).contains("经营机构负责人驳回"));
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private StartProcessCmd startCmd(String bizId, String title) {
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("LOAN");
        cmd.setBizId(bizId);
        cmd.setBusinessKey("LOAN:" + bizId);
        cmd.setProcessDefinitionKey("loan_approve_v1");
        cmd.setStartUser("E10001");
        cmd.setStartOrgId("001001");
        cmd.setTitle(title);
        cmd.setVariables(Map.of("bizId", bizId, "title", title));
        return cmd;
    }

    private long queryTodoCount(MockHttpSession session) throws Exception {
        JsonNode page = queryTodoPage(session);
        return page.path("total").asLong();
    }

    private JsonNode queryFirstTodo(MockHttpSession session) throws Exception {
        JsonNode page = queryTodoPage(session);
        assertThat(page.path("total").asLong()).isPositive();
        return page.path("records").get(0);
    }

    private JsonNode queryTodoPage(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/workflow/tasks").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("page");
    }

    private void claim(MockHttpSession session, String taskId) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/claim", taskId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void approve(MockHttpSession session, String taskId, String opinion, Map<String, Object> formData) throws Exception {
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

    private void reject(MockHttpSession session, String taskId, String opinion) throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/reject", taskId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("opinion", opinion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void assertProcessStatus(String processInstanceId, String expectedStatus) {
        String sql = "SELECT process_status FROM BIZ_PROCESS_MAP WHERE process_instance_id = ?";
        assertThat(jdbcTemplate.queryForObject(sql, String.class, processInstanceId)).isEqualTo(expectedStatus);
    }

    private void assertCurrentAssignee(String processInstanceId, String expectedAssignee) {
        String sql = "SELECT current_assignee FROM BIZ_PROCESS_MAP WHERE process_instance_id = ?";
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

    private int assertCommentCount(String processInstanceId, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ACT_HI_COMMENT WHERE PROC_INST_ID_ = ?",
                Integer.class,
                processInstanceId
        );
        assertThat(count).isEqualTo(expectedCount);
        return count;
    }

    private List<String> loadComments(String processInstanceId) {
        return jdbcTemplate.queryForList(
                "SELECT MESSAGE_ FROM ACT_HI_COMMENT WHERE PROC_INST_ID_ = ? ORDER BY TIME_ ASC",
                String.class,
                processInstanceId
        );
    }
}
