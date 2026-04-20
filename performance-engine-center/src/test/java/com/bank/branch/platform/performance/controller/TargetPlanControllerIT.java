package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.CreateTargetPlanReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateTargetPlanReqDTO;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TargetPlanController IT: 覆盖 4 端点 (list / getById / create / update) 正反向 + 鉴权/审计注解.
 *
 * <p>Plan 1405-1407 行钦定必含场景:
 * <ul>
 *   <li>put_whenReasonMissing_returns200 (update 不强制 reason)</li>
 *   <li>post_whenKpiSchemeMissing_returns409</li>
 * </ul>
 *
 * <p>测试数据前缀: TEST_TGT_* (方案) + TEST_KPI_* (KPI 方案),
 * 事务 @Transactional + @Rollback 自动清理.
 */
class TargetPlanControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.TargetPlanController";

    @Autowired
    private PerfTargetPlanMapper planMapper;

    @Autowired
    private PerfKpiSchemeMapper kpiSchemeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // =================== list ===================

    @Test
    void list_withDefaultPaging_returns200() throws Exception {
        PerfKpiScheme scheme = seedKpiScheme("LIST_A");
        planMapper.insert(TargetTestDataBuilder.plan("PAGE_A", scheme.getId()));
        planMapper.insert(TargetTestDataBuilder.plan("PAGE_B", scheme.getId()));

        mockMvc.perform(get("/api/perf/target-plans")
                        .param("keyword", "TEST_TGT_PAGE")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.page.pageSize").value(10))
                .andExpect(jsonPath("$.page.total").value(2));
    }

    // =================== getById ===================

    @Test
    void getById_whenExists_returns200() throws Exception {
        PerfKpiScheme scheme = seedKpiScheme("GET_OK");
        PerfTargetPlan plan = TargetTestDataBuilder.plan("GET_OK", scheme.getId());
        planMapper.insert(plan);

        mockMvc.perform(get("/api/perf/target-plans/{id}", plan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(plan.getId()))
                .andExpect(jsonPath("$.data.planCode").value("TEST_TGT_GET_OK"));
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        // 业务异常 TARGET_PLAN_NOT_FOUND (PERF-40403) 冒泡到全局异常处理器
        mockMvc.perform(get("/api/perf/target-plans/{id}", "NON_EXIST_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40403"));
    }

    // =================== create ===================

    @Test
    void create_whenSuccess_returns200() throws Exception {
        PerfKpiScheme scheme = seedKpiScheme("CREATE_OK");
        CreateTargetPlanReqDTO req = createReq("CREATE_OK", scheme.getId());

        mockMvc.perform(post("/api/perf/target-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.planCode").value("TEST_TGT_CREATE_OK"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(planMapper.selectByPlanCode("TEST_TGT_CREATE_OK")).isNotNull();
    }

    @Test
    void post_whenPlanCodeDup_returns409() throws Exception {
        // 业务异常 TARGET_PLAN_CODE_DUP (PERF-40908)
        PerfKpiScheme scheme = seedKpiScheme("DUP_CODE");
        planMapper.insert(TargetTestDataBuilder.plan("CREATE_DUP", scheme.getId()));
        CreateTargetPlanReqDTO req = createReq("CREATE_DUP", scheme.getId());

        mockMvc.perform(post("/api/perf/target-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40908"));
    }

    @Test
    void post_whenKpiSchemeMissing_returns409() throws Exception {
        // Plan L1407 钦定: 引用的 kpiSchemeId 不存在 → PERF-40915 (409 语义)
        CreateTargetPlanReqDTO req = createReq("KPI_MISSING", "NON_EXIST_KPI_ID");

        mockMvc.perform(post("/api/perf/target-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40915"));
    }

    @Test
    void create_whenPlanCodeLowercase_returns400() throws Exception {
        // Pattern 校验 failed
        PerfKpiScheme scheme = seedKpiScheme("LOW");
        CreateTargetPlanReqDTO req = createReq("OK", scheme.getId());
        req.setPlanCode("lower_bad");

        mockMvc.perform(post("/api/perf/target-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =================== update ===================

    @Test
    void update_whenPartialUpdate_returns200() throws Exception {
        PerfKpiScheme scheme = seedKpiScheme("UPD_OK");
        PerfTargetPlan plan = TargetTestDataBuilder.plan("UPDATE_OK", scheme.getId());
        planMapper.insert(plan);

        UpdateTargetPlanReqDTO req = new UpdateTargetPlanReqDTO();
        req.setPlanName("新名称");
        req.setTargetCycle("QUARTER");

        mockMvc.perform(put("/api/perf/target-plans/{id}", plan.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.planName").value("新名称"))
                .andExpect(jsonPath("$.data.targetCycle").value("QUARTER"));
    }

    @Test
    void put_whenReasonMissing_returns200() throws Exception {
        // Plan L1406 钦定: update 不强制 reason, 空 body {} 应 200
        PerfKpiScheme scheme = seedKpiScheme("UPD_NOR");
        PerfTargetPlan plan = TargetTestDataBuilder.plan("UPDATE_NOREQ", scheme.getId());
        planMapper.insert(plan);

        mockMvc.perform(put("/api/perf/target-plans/{id}", plan.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void update_whenPlanNotFound_returns404() throws Exception {
        UpdateTargetPlanReqDTO req = new UpdateTargetPlanReqDTO();
        req.setPlanName("任意");

        mockMvc.perform(put("/api/perf/target-plans/{id}", "NON_EXIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40403"));
    }

    // =================== 注解约束 ===================

    @Test
    void controller_shouldEnableMethodValidation() throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        assertThat(controllerClass.getAnnotation(Validated.class)).isNotNull();
    }

    @Test
    void readMethods_shouldDeclareBizAuth() throws Exception {
        assertBizAuth("list", new Class<?>[]{
                String.class, String.class, String.class, int.class, int.class
        }, BizAction.LIST);
        assertBizAuth("getById", new Class<?>[]{String.class}, BizAction.READ);
    }

    @Test
    void writeMethods_shouldDeclareExpectedBizAuthAndAuditLog() throws Exception {
        assertBizAuth("create", new Class<?>[]{CreateTargetPlanReqDTO.class}, BizAction.WRITE);
        assertAuditLog("create", new Class<?>[]{CreateTargetPlanReqDTO.class}, "CREATE", false);

        assertBizAuth("update", new Class<?>[]{String.class, UpdateTargetPlanReqDTO.class}, BizAction.WRITE);
        // Plan L1406: update 不强制 reason, 故 reasonRequired=false
        assertAuditLog("update", new Class<?>[]{String.class, UpdateTargetPlanReqDTO.class}, "UPDATE", false);
    }

    // =================== helpers ===================

    /** seed 一条 ACTIVE 的 KPI 方案, 返回落库后的实体. */
    private PerfKpiScheme seedKpiScheme(String suffix) {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("TGTP_" + suffix);
        kpiSchemeMapper.insert(scheme);
        return scheme;
    }

    private static CreateTargetPlanReqDTO createReq(String suffix, String kpiSchemeId) {
        CreateTargetPlanReqDTO req = new CreateTargetPlanReqDTO();
        req.setPlanCode("TEST_TGT_" + suffix);
        req.setPlanName("目标方案-" + suffix);
        req.setKpiSchemeId(kpiSchemeId);
        req.setTargetDim("EMP");
        req.setTargetCycle("YEAR");
        req.setEffectiveDate(LocalDate.now());
        return req;
    }

    private void assertBizAuth(String methodName, Class<?>[] parameterTypes, BizAction action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).as("method %s 应有 @BizAuth", methodName).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(action);
    }

    private void assertAuditLog(String methodName,
                                Class<?>[] parameterTypes,
                                String action,
                                boolean reasonRequired) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).as("method %s 应有 @AuditLog", methodName).isNotNull();
        assertThat(auditLog.action()).isEqualTo(action);
        assertThat(auditLog.resourceType()).isEqualTo("TARGET_PLAN");
        assertThat(auditLog.reasonRequired()).isEqualTo(reasonRequired);
    }
}
