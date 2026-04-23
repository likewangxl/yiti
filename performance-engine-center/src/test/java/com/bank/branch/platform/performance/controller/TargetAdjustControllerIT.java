package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.TargetAdjustCreateReqDTO;
import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.mapper.PerfTargetAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TargetAdjustController IT (V1.2 Q3.2c).
 *
 * <p>覆盖 4 个端点：
 * <ul>
 *   <li>POST /api/perf/target-adjust/create</li>
 *   <li>GET /api/perf/target-adjust/{id}</li>
 *   <li>GET /api/perf/target-adjust/list</li>
 *   <li>POST /api/perf/target-adjust/{id}/withdraw</li>
 * </ul>
 *
 * <p>注解约束：
 * <ul>
 *   <li>所有端点 {@code @BizAuth(PERF_CONFIG, <具体 action>)}</li>
 *   <li>create / withdraw 必须 {@code @AuditLog(reasonRequired=true)}</li>
 * </ul>
 */
class TargetAdjustControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.TargetAdjustController";

    @MockBean
    private CurrentUserApi currentUserApi;

    @MockBean
    private WorkflowApi workflowApi;

    @Autowired
    private PerfTargetAdjustApplyMapper applyMapper;

    @Autowired
    private PerfTargetPlanMapper targetPlanMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private String existingPlanId;

    @BeforeEach
    void setUp() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);

        Mockito.when(workflowApi.startProcess(Mockito.any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_TAA_IT_0001", null, null));

        // 插入真实的 target_plan（避免 TARGET_PLAN_NOT_FOUND）
        existingPlanId = "TEST_TAA_PLAN_" + UUID.randomUUID().toString().substring(0, 8);
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId(existingPlanId);
        plan.setPlanCode("TEST_TAA_PC_" + existingPlanId);
        plan.setPlanName("TAA IT 测试方案");
        plan.setStatus("ACTIVE");
        plan.setEffectiveDate(java.time.LocalDate.now());
        plan.setCreatedBy("admin");
        plan.setCreatedTime(LocalDateTime.now());
        plan.setUpdatedBy("admin");
        plan.setUpdatedTime(LocalDateTime.now());
        targetPlanMapper.insert(plan);
    }

    private PerfTargetAdjustApply buildExisting(String idSuffix, String status,
                                                String subjectType, String subjectId) {
        PerfTargetAdjustApply a = new PerfTargetAdjustApply();
        a.setId("TEST_TAA_IT_" + idSuffix);
        a.setPlanId(existingPlanId);
        a.setSubjectType(subjectType);
        a.setSubjectId(subjectId);
        a.setCycleKey("2026Q1");
        a.setStatus(status);
        a.setBusinessKey("TARGET_ADJUST:TEST_TAA_IT_" + idSuffix);
        a.setProcessInstanceId("PI_IT_" + idSuffix);
        a.setOwnerOrgId("ORG_IT");
        a.setRemark("{\"adjustments\":[{\"metricCode\":\"M_IT_1\",\"newValue\":200}],\"reason\":\"IT\"}");
        a.setCreatedBy("admin");
        a.setCreatedTime(LocalDateTime.now());
        a.setUpdatedBy("admin");
        a.setUpdatedTime(LocalDateTime.now());
        return a;
    }

    // ============= create =============

    @Test
    void create_happyPath_returns200_andPersists() throws Exception {
        TargetAdjustCreateReqDTO req = new TargetAdjustCreateReqDTO();
        req.setPlanId(existingPlanId);
        req.setSubjectType("EMP");
        req.setSubjectId("EMP_IT_001");
        req.setCycleKey("2026Q1");
        req.setOwnerOrgId("ORG_IT");
        req.setReason("IT 测试目标上调");
        TargetAdjustCreateReqDTO.Adjustment a1 = new TargetAdjustCreateReqDTO.Adjustment();
        a1.setMetricCode("M_IT_DEP");
        a1.setOldValue(new BigDecimal("100"));
        a1.setNewValue(new BigDecimal("120"));
        req.setAdjustments(java.util.List.of(a1));

        mockMvc.perform(post("/api/perf/target-adjust/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"));
    }

    @Test
    void create_emptyAdjustments_returnsValidationFailed() throws Exception {
        TargetAdjustCreateReqDTO req = new TargetAdjustCreateReqDTO();
        req.setPlanId(existingPlanId);
        req.setSubjectType("EMP");
        req.setSubjectId("EMP_IT_002");
        req.setCycleKey("2026Q1");
        req.setOwnerOrgId("ORG_IT");
        req.setReason("IT 测试");
        req.setAdjustments(java.util.Collections.emptyList());

        mockMvc.perform(post("/api/perf/target-adjust/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    @Test
    void create_planNotFound_returnsPlanNotFound() throws Exception {
        TargetAdjustCreateReqDTO req = new TargetAdjustCreateReqDTO();
        req.setPlanId("NO_SUCH_PLAN_TAA");
        req.setSubjectType("EMP");
        req.setSubjectId("EMP_IT_003");
        req.setCycleKey("2026Q1");
        req.setOwnerOrgId("ORG_IT");
        req.setReason("IT 测试");
        TargetAdjustCreateReqDTO.Adjustment a1 = new TargetAdjustCreateReqDTO.Adjustment();
        a1.setMetricCode("M_IT_DEP");
        a1.setNewValue(new BigDecimal("120"));
        req.setAdjustments(java.util.List.of(a1));

        mockMvc.perform(post("/api/perf/target-adjust/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40004"));
    }

    // ============= getById =============

    @Test
    void getById_returns200() throws Exception {
        PerfTargetAdjustApply apply = buildExisting("GET_OK", "IN_APPROVAL", "EMP", "EMP_GET");
        applyMapper.insert(apply);

        mockMvc.perform(get("/api/perf/target-adjust/{id}", apply.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(apply.getId()))
                .andExpect(jsonPath("$.data.planId").value(existingPlanId))
                .andExpect(jsonPath("$.data.subjectType").value("EMP"))
                .andExpect(jsonPath("$.data.subjectId").value("EMP_GET"))
                .andExpect(jsonPath("$.data.cycleKey").value("2026Q1"))
                .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"));
    }

    @Test
    void getById_notFound_returnsTargetAdjustApplyNotFound() throws Exception {
        mockMvc.perform(get("/api/perf/target-adjust/{id}", "NO_SUCH_APPLY_TAA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40020"));
    }

    // ============= list =============

    @Test
    void list_withFilters_returnsPage() throws Exception {
        applyMapper.insert(buildExisting("LIST_A", "IN_APPROVAL", "EMP", "EMP_LA"));
        applyMapper.insert(buildExisting("LIST_B", "APPROVED", "ORG", "ORG_LB"));

        mockMvc.perform(get("/api/perf/target-adjust/list")
                        .param("status", "IN_APPROVAL")
                        .param("subjectType", "EMP")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.pageNo").value(1));
    }

    // ============= withdraw =============

    @Test
    void withdraw_inApproval_transitionsToRejected() throws Exception {
        PerfTargetAdjustApply apply = buildExisting("WITHDRAW", "IN_APPROVAL", "EMP", "EMP_WD");
        applyMapper.insert(apply);

        String reasonBody = "{\"reason\": \"取消申请\"}";
        mockMvc.perform(post("/api/perf/target-adjust/{id}/withdraw", apply.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        PerfTargetAdjustApply after = applyMapper.selectById(apply.getId());
        assertThat(after.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void withdraw_notFound_returnsTargetAdjustApplyNotFound() throws Exception {
        mockMvc.perform(post("/api/perf/target-adjust/{id}/withdraw", "NO_SUCH_WD_TAA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"err\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40020"));
    }

    // ============= 注解约束 =============

    @Test
    void create_hasBizAuthWriteAndAuditLogWithReason() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method m = Arrays.stream(clazz.getDeclaredMethods())
                .filter(x -> x.getName().equals("create"))
                .findFirst()
                .orElseThrow();
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.WRITE);
        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.reasonRequired()).isTrue();
    }

    @Test
    void withdraw_hasBizAuthAndAuditLogWithReason() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method m = Arrays.stream(clazz.getDeclaredMethods())
                .filter(x -> x.getName().equals("withdraw"))
                .findFirst()
                .orElseThrow();
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.reasonRequired()).isTrue();
    }

    @Test
    void getById_hasBizAuthRead() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method m = Arrays.stream(clazz.getDeclaredMethods())
                .filter(x -> x.getName().equals("getById"))
                .findFirst()
                .orElseThrow();
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.READ);
    }
}
