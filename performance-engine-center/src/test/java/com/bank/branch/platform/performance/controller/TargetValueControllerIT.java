package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueBatchReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueReqDTO;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TargetValueController IT: 覆盖 3 端点 (list / create-upsert / batch) 正反向 + 鉴权/审计注解.
 *
 * <p>Plan 1410-1412 行钦定必含场景:
 * <ul>
 *   <li>batchPost_when501Items_returns400</li>
 *   <li>batchPost_when500Items_returns200</li>
 *   <li>batchPost_whenEmptyList_returns400</li>
 * </ul>
 *
 * <p>测试数据前缀: TEST_TGT_* (方案) + TEST_KPI_* (KPI 方案),
 * 事务 @Transactional + @Rollback 自动清理.
 */
class TargetValueControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.TargetValueController";

    @Autowired
    private PerfTargetPlanMapper planMapper;

    @Autowired
    private PerfTargetValueMapper valueMapper;

    @Autowired
    private PerfKpiSchemeMapper kpiSchemeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // =================== list ===================

    @Test
    void list_byPlanId_returns200() throws Exception {
        PerfTargetPlan plan = seedPlan("LIST_OK");
        valueMapper.upsertBatch(List.of(
                TargetTestDataBuilder.value(plan.getId(), "EMP", "E001", "2026", "TEST_METRIC_X",
                        new BigDecimal("100.0000")),
                TargetTestDataBuilder.value(plan.getId(), "EMP", "E002", "2026", "TEST_METRIC_X",
                        new BigDecimal("200.0000"))));

        mockMvc.perform(get("/api/perf/target-values")
                        .param("planId", plan.getId())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(2));
    }

    @Test
    void list_whenPlanIdMissing_returns400() throws Exception {
        // Controller 方法级 @Validated + @NotBlank 拦截, 缺失 planId 应 400
        mockMvc.perform(get("/api/perf/target-values")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest());
    }

    // =================== create (单条 upsert) ===================

    @Test
    void post_singleUpsert_returns200() throws Exception {
        PerfTargetPlan plan = seedPlan("SINGLE_OK");
        UpsertTargetValueReqDTO req = singleReq(plan.getId(), "EMP", "E001",
                "2026", "TEST_METRIC_Y", "150.0000");

        mockMvc.perform(post("/api/perf/target-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        PerfTargetValue inserted = valueMapper.selectByUniqueKey(
                plan.getId(), "EMP", "E001", "2026", "TEST_METRIC_Y");
        assertThat(inserted).isNotNull();
        assertThat(inserted.getTargetValue()).isEqualByComparingTo("150.0000");
    }

    @Test
    void post_whenCycleKeyInvalid_returns400() throws Exception {
        PerfTargetPlan plan = seedPlan("BAD_CYCLE");
        UpsertTargetValueReqDTO req = singleReq(plan.getId(), "EMP", "E001",
                "abcd", "TEST_METRIC_Y", "100");
        req.setCycleKey("abcd"); // 破坏格式

        mockMvc.perform(post("/api/perf/target-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =================== batch upsert ===================

    @Test
    void batchPost_whenEmptyList_returns400() throws Exception {
        // Plan 1412 行钦定: values 为空列表 → @NotEmpty 触发 400
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        req.setValues(List.of());

        mockMvc.perform(post("/api/perf/target-values/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchPost_when500Items_returns200() throws Exception {
        // Plan 1411 行钦定: 500 条恰好在上限内, 应 200
        PerfTargetPlan plan = seedPlan("BATCH_500");
        List<UpsertTargetValueReqDTO> list = new ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            list.add(singleReq(plan.getId(), "EMP",
                    "E" + String.format("%04d", i), "2026", "TEST_METRIC_B", "10"));
        }
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        req.setValues(list);

        mockMvc.perform(post("/api/perf/target-values/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void batchPost_when501Items_returns400() throws Exception {
        // Plan 1410 行钦定: 501 条超过 @Size(max=500), 应 400
        PerfTargetPlan plan = seedPlan("BATCH_501");
        List<UpsertTargetValueReqDTO> list = new ArrayList<>(501);
        for (int i = 0; i < 501; i++) {
            list.add(singleReq(plan.getId(), "EMP",
                    "E" + String.format("%04d", i), "2026", "TEST_METRIC_C", "1"));
        }
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        req.setValues(list);

        mockMvc.perform(post("/api/perf/target-values/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchPost_whenDuplicateUK_updatesValue() throws Exception {
        // 集成测真 upsert: 插入 1 条, 再用相同 UK 的 batch 覆盖 target_value
        PerfTargetPlan plan = seedPlan("BATCH_DUP");
        valueMapper.upsertBatch(List.of(TargetTestDataBuilder.value(
                plan.getId(), "EMP", "E_DUP", "2026", "TEST_METRIC_D", new BigDecimal("100.0000"))));

        UpsertTargetValueReqDTO upd = singleReq(plan.getId(), "EMP",
                "E_DUP", "2026", "TEST_METRIC_D", "200.0000");
        UpsertTargetValueBatchReqDTO req = new UpsertTargetValueBatchReqDTO();
        req.setValues(List.of(upd));

        mockMvc.perform(post("/api/perf/target-values/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        PerfTargetValue after = valueMapper.selectByUniqueKey(
                plan.getId(), "EMP", "E_DUP", "2026", "TEST_METRIC_D");
        assertThat(after).isNotNull();
        assertThat(after.getTargetValue()).isEqualByComparingTo("200.0000");
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
                String.class, String.class, String.class, String.class, int.class, int.class
        }, BizAction.LIST);
    }

    @Test
    void writeMethods_shouldDeclareExpectedBizAuthAndAuditLog() throws Exception {
        assertBizAuth("create", new Class<?>[]{UpsertTargetValueReqDTO.class}, BizAction.WRITE);
        assertAuditLog("create", new Class<?>[]{UpsertTargetValueReqDTO.class}, "CREATE", false);

        assertBizAuth("batch", new Class<?>[]{UpsertTargetValueBatchReqDTO.class}, BizAction.WRITE);
        assertAuditLog("batch", new Class<?>[]{UpsertTargetValueBatchReqDTO.class}, "CREATE", false);
    }

    // =================== helpers ===================

    private PerfTargetPlan seedPlan(String suffix) {
        // 先 seed 一个 ACTIVE KPI scheme 供目标方案引用 (FK 仅逻辑绑定, DB 无 FK 约束)
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("TGTV_" + suffix);
        kpiSchemeMapper.insert(scheme);
        PerfTargetPlan plan = TargetTestDataBuilder.plan(suffix, scheme.getId());
        planMapper.insert(plan);
        return plan;
    }

    private static UpsertTargetValueReqDTO singleReq(String planId, String subjectType, String subjectId,
                                                     String cycleKey, String metricCode, String targetValue) {
        UpsertTargetValueReqDTO req = new UpsertTargetValueReqDTO();
        req.setPlanId(planId);
        req.setSubjectType(subjectType);
        req.setSubjectId(subjectId);
        req.setCycleKey(cycleKey);
        req.setMetricCode(metricCode);
        req.setTargetValue(new BigDecimal(targetValue));
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
        assertThat(auditLog.resourceType()).isEqualTo("TARGET_VALUE");
        assertThat(auditLog.reasonRequired()).isEqualTo(reasonRequired);
    }
}
