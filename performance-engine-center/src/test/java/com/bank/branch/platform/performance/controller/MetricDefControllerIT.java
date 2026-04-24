package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.performance.controller.dto.ChangeStatusReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateMetricReqDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateMetricReqDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.facade.MetricLifecycleFacade;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricRefMapper;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.ConstraintViolationException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricDefControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.MetricDefController";

    @Autowired
    private PerfMetricDefMapper metricDefMapper;

    @Autowired
    private PerfMetricRefMapper metricRefMapper;

    @Autowired
    private MetricDefService metricDefService;

    @SpyBean
    private MetricLifecycleFacade metricLifecycleFacade;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUpFacadeSpy() {
        doAnswer(invocation -> metricDefService.create(invocation.getArgument(0, CreateMetricDefCmd.class)))
                .when(metricLifecycleFacade)
                .createMetric(any(CreateMetricDefCmd.class));
    }

    @Test
    void list_shouldReturnPageResult() throws Exception {
        metricDefMapper.insert(metric("PAGE_A", 1));
        metricDefMapper.insert(metric("PAGE_B", 2));

        mockMvc.perform(get("/api/perf/metrics")
                        .param("baseDim", "EMP")
                        .param("status", "ACTIVE")
                        .param("keyword", "TEST_METRIC_PAGE")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.page.pageSize").value(10))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.records.length()").value(2))
                .andExpect(jsonPath("$.page.records[0].metricCode").value("TEST_METRIC_PAGE_A"));
    }

    @Test
    void getByCode_whenExists_returns200() throws Exception {
        metricDefMapper.insert(metric("GET_OK", 1));

        mockMvc.perform(get("/api/perf/metrics/{metricCode}", "TEST_METRIC_GET_OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.metricCode").value("TEST_METRIC_GET_OK"))
                .andExpect(jsonPath("$.data.baseDim").value("EMP"))
                .andExpect(jsonPath("$.data.metricLevel").value(1));
    }

    @Test
    void getByCode_whenMissing_returnsBizError() throws Exception {
        // Q8.5a 对齐 PerfErrorCode §K：指标不存在 → PERF-40001
        mockMvc.perform(get("/api/perf/metrics/{metricCode}", "TEST_METRIC_MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40001"));
    }

    @Test
    void listRefs_whenExists_returns200() throws Exception {
        PerfMetricDef main = metric("REF_MAIN", 2);
        main.setRefMetricCodes("[\"TEST_METRIC_REF_A\",\"TEST_METRIC_REF_B\"]");
        metricDefMapper.insert(main);
        metricDefMapper.insert(metric("REF_A", 1));
        metricDefMapper.insert(metric("REF_B", 1));
        metricRefMapper.insertBatch(java.util.List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_REF_MAIN", "TEST_METRIC_REF_A"),
                MetricTestDataBuilder.ref("TEST_METRIC_REF_MAIN", "TEST_METRIC_REF_B")
        ));

        mockMvc.perform(get("/api/perf/metrics/{metricCode}/refs", "TEST_METRIC_REF_MAIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", containsInAnyOrder("TEST_METRIC_REF_A", "TEST_METRIC_REF_B")));
    }

    @Test
    void listRefBy_whenExists_returns200() throws Exception {
        metricDefMapper.insert(metric("REF_BY_TARGET", 1));
        metricDefMapper.insert(metric("REF_BY_UP_1", 2));
        metricDefMapper.insert(metric("REF_BY_UP_2", 2));
        metricRefMapper.insertBatch(java.util.List.of(
                MetricTestDataBuilder.ref("TEST_METRIC_REF_BY_UP_1", "TEST_METRIC_REF_BY_TARGET"),
                MetricTestDataBuilder.ref("TEST_METRIC_REF_BY_UP_2", "TEST_METRIC_REF_BY_TARGET")
        ));

        mockMvc.perform(get("/api/perf/metrics/{metricCode}/ref-by", "TEST_METRIC_REF_BY_TARGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", containsInAnyOrder("TEST_METRIC_REF_BY_UP_1", "TEST_METRIC_REF_BY_UP_2")));
    }

    @Test
    void listSlots_returns200() throws Exception {
        metricDefMapper.insert(metric("SLOT_3", 1, 3));
        metricDefMapper.insert(metric("SLOT_7", 2, 7));

        mockMvc.perform(get("/api/perf/metrics/val-slots")
                        .param("baseDim", "EMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", containsInAnyOrder(3, 7)));
    }

    @Test
    void create_whenPayloadInvalid_returns400() throws Exception {
        CreateMetricReqDTO req = new CreateMetricReqDTO();
        req.setMetricCode("metric_lowercase");
        req.setMetricName("invalid");
        req.setBaseDim("EMP");
        req.setMetricLevel(1);
        req.setCalcFreq("DAY");
        req.setCalcMode("AUTO");

        mockMvc.perform(post("/api/perf/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenMetricCodeDup_returnsBizError() throws Exception {
        metricDefMapper.insert(metric("CREATE_DUP", 1));

        mockMvc.perform(post("/api/perf/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq("TEST_METRIC_CREATE_DUP"))))
                .andExpect(status().isOk())
                // Q8.5a 对齐 PerfErrorCode §K：指标编码已存在 → PERF-40901
                .andExpect(jsonPath("$.code").value("PERF-40901"));
    }

    @Test
    void create_whenSuccess_returns200() throws Exception {
        mockMvc.perform(post("/api/perf/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq("TEST_METRIC_CREATE_OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.metricCode").value("TEST_METRIC_CREATE_OK"))
                .andExpect(jsonPath("$.data.baseDim").value("EMP"))
                .andExpect(jsonPath("$.data.metricLevel").value(1));

        assertThat(metricDefMapper.selectByMetricCode("TEST_METRIC_CREATE_OK")).isNotNull();
    }

    @Test
    void update_whenSuccess_returns200() throws Exception {
        metricDefMapper.insert(metric("UPDATE_OK", 1, 11));

        UpdateMetricReqDTO req = new UpdateMetricReqDTO();
        req.setMetricName("updated-metric-name");
        req.setMetricDesc("updated-desc");
        req.setCalcFreq("MONTH");
        req.setCalcMode("MANUAL");
        req.setCalcLogicType("SQL");
        req.setSqlText("SELECT 2");
        req.setRefMetricCodes("[]");

        mockMvc.perform(put("/api/perf/metrics/{metricCode}", "TEST_METRIC_UPDATE_OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.metricCode").value("TEST_METRIC_UPDATE_OK"))
                .andExpect(jsonPath("$.data.metricName").value("updated-metric-name"))
                .andExpect(jsonPath("$.data.metricDesc").value("updated-desc"))
                .andExpect(jsonPath("$.data.calcFreq").value("MONTH"))
                .andExpect(jsonPath("$.data.calcMode").value("MANUAL"));
    }

    @Test
    void delete_whenSuccess_marksMetricDisabled() throws Exception {
        metricDefMapper.insert(metric("DELETE_OK", 1, 31));
        ReleaseSlotReqDTO req = new ReleaseSlotReqDTO();
        req.setReason("delete metric");

        mockMvc.perform(delete("/api/perf/metrics/{metricCode}", "TEST_METRIC_DELETE_OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertThat(metricDefMapper.selectByMetricCode("TEST_METRIC_DELETE_OK").getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void delete_whenReasonMissing_returns400() throws Exception {
        metricDefMapper.insert(metric("DELETE_REASON", 1));

        mockMvc.perform(delete("/api/perf/metrics/{metricCode}", "TEST_METRIC_DELETE_REASON")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReleaseSlotReqDTO())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusChange_whenReasonMissing_returns400() throws Exception {
        metricDefMapper.insert(metric("STATUS_REASON", 1));
        ChangeStatusReqDTO req = new ChangeStatusReqDTO();
        req.setStatus("DISABLED");

        mockMvc.perform(put("/api/perf/metrics/{metricCode}/status", "TEST_METRIC_STATUS_REASON")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusChange_whenSuccess_marksMetricDisabled() throws Exception {
        metricDefMapper.insert(metric("STATUS_OK", 1, 41));
        ChangeStatusReqDTO req = new ChangeStatusReqDTO();
        req.setStatus("DISABLED");
        req.setReason("status disable");

        mockMvc.perform(put("/api/perf/metrics/{metricCode}/status", "TEST_METRIC_STATUS_OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertThat(metricDefMapper.selectByMetricCode("TEST_METRIC_STATUS_OK").getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void releaseSlot_whenMetricNotDisabled_returnsBizError() throws Exception {
        metricDefMapper.insert(metric("RELEASE_ACTIVE", 1, 21));
        ReleaseSlotReqDTO req = new ReleaseSlotReqDTO();
        req.setReason("release active metric");

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/slot/release", "TEST_METRIC_RELEASE_ACTIVE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // Q8.5a 对齐 PerfErrorCode §K：非禁用指标不得释放槽位 → VALIDATION_FAILED → PERF-42200
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    @Test
    void releaseSlot_whenSuccess_clearsOccupiedSlot() throws Exception {
        PerfMetricDef disabled = metric("RELEASE_OK", 1, 51);
        disabled.setStatus("DISABLED");
        metricDefMapper.insert(disabled);
        ReleaseSlotReqDTO req = new ReleaseSlotReqDTO();
        req.setReason("release slot");

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/slot/release", "TEST_METRIC_RELEASE_OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        PerfMetricDef reloaded = metricDefMapper.selectByMetricCode("TEST_METRIC_RELEASE_OK");
        assertThat(reloaded.getValSlot()).isNull();
    }

    @Test
    void list_whenPageNoIsZero_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/perf/metrics")
                        .param("pageNo", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYS_500"))
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ConstraintViolationException.class);
                    assertThat(result.getResolvedException()).hasMessageContaining("pageNo");
                });
    }

    @Test
    void list_whenPageSizeTooLarge_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/perf/metrics")
                        .param("pageNo", "1")
                        .param("pageSize", "101"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYS_500"))
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ConstraintViolationException.class);
                    assertThat(result.getResolvedException()).hasMessageContaining("pageSize");
                });
    }

    @Test
    void list_whenPageSizeIsZero_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/perf/metrics")
                        .param("pageNo", "1")
                        .param("pageSize", "0"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYS_500"))
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ConstraintViolationException.class);
                    assertThat(result.getResolvedException()).hasMessageContaining("pageSize");
                });
    }

    @Test
    void listSlots_whenBaseDimBlank_returnsBadRequestAndUnifiedError() throws Exception {
        mockMvc.perform(get("/api/perf/metrics/val-slots")
                        .param("baseDim", " "))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYS_500"))
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ConstraintViolationException.class);
                    assertThat(result.getResolvedException()).hasMessageContaining("baseDim");
                });
    }

    @Test
    void readMethods_shouldDeclareBizAuth() throws Exception {
        assertBizAuth("list", new Class<?>[]{
                String.class, Integer.class, String.class, String.class, int.class, int.class
        }, BizAction.LIST);
        assertBizAuth("getByCode", new Class<?>[]{String.class}, BizAction.READ);
        assertBizAuth("listRefs", new Class<?>[]{String.class}, BizAction.READ);
        assertBizAuth("listRefBy", new Class<?>[]{String.class}, BizAction.READ);
        assertBizAuth("listSlots", new Class<?>[]{String.class}, BizAction.READ);
    }

    @Test
    void controller_shouldEnableMethodValidation() throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        assertThat(controllerClass.getAnnotation(Validated.class)).isNotNull();
    }

    @Test
    void writeMethods_shouldDeclareExpectedBizAuthAndAuditLog() throws Exception {
        assertBizAuth("create", new Class<?>[]{CreateMetricReqDTO.class}, BizAction.WRITE);
        assertNoAuditLog("create", new Class<?>[]{CreateMetricReqDTO.class});

        assertBizAuth("update", new Class<?>[]{String.class, UpdateMetricReqDTO.class}, BizAction.WRITE);
        assertNoAuditLog("update", new Class<?>[]{String.class, UpdateMetricReqDTO.class});

        assertBizAuth("delete", new Class<?>[]{String.class, ReleaseSlotReqDTO.class}, BizAction.DELETE);
        assertAuditLog("delete", new Class<?>[]{String.class, ReleaseSlotReqDTO.class}, "DELETE");

        assertBizAuth("changeStatus", new Class<?>[]{String.class, ChangeStatusReqDTO.class}, BizAction.CONFIG);
        assertAuditLog("changeStatus", new Class<?>[]{String.class, ChangeStatusReqDTO.class}, "STATUS_CHANGE");

        assertBizAuth("releaseSlot", new Class<?>[]{String.class, ReleaseSlotReqDTO.class}, BizAction.CONFIG);
        assertAuditLog("releaseSlot", new Class<?>[]{String.class, ReleaseSlotReqDTO.class}, "SLOT_RELEASE");
    }

    private void assertBizAuth(String methodName, Class<?>[] parameterTypes, BizAction action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(action);
    }

    private void assertAuditLog(String methodName,
                                Class<?>[] parameterTypes,
                                String action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.action()).isEqualTo(action);
        assertThat(auditLog.resourceType()).isEqualTo("METRIC_DEF");
        assertThat(auditLog.reasonRequired()).isTrue();
    }

    private void assertNoAuditLog(String methodName, Class<?>[] parameterTypes) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(AuditLog.class)).isNull();
    }

    // ===================== Task D1 Red：断言响应体 DTO 化，不含 entity 内部字段 =====================

    /**
     * [Red] list 接口响应不应泄漏 entity 内部字段 deleted / createdTime / updatedTime.
     * 当前 Controller 直接返回 PerfMetricDef entity，该测试应失败。
     */
    @Test
    void list_shouldNotExposeEntityFields() throws Exception {
        metricDefMapper.insert(metric("DTO_LIST_A", 1, 91));

        String body = mockMvc.perform(get("/api/perf/metrics")
                        .param("baseDim", "EMP")
                        .param("keyword", "TEST_METRIC_DTO_LIST_A")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 断言 DTO 字段存在
        assertThat(body).contains("\"metricCode\"");
        assertThat(body).contains("\"metricName\"");
        // 断言 entity 内部字段不存在
        assertThat(body).doesNotContain("\"deleted\"");
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"createdBy\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }

    /**
     * [Red] getByCode 接口响应不应泄漏 entity 内部字段.
     */
    @Test
    void getByCode_shouldNotExposeEntityFields() throws Exception {
        metricDefMapper.insert(metric("DTO_GET_A", 1, 92));

        String body = mockMvc.perform(get("/api/perf/metrics/{metricCode}", "TEST_METRIC_DTO_GET_A"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 断言 DTO 字段存在
        assertThat(body).contains("\"metricCode\"");
        // 断言 entity 内部字段不存在
        assertThat(body).doesNotContain("\"deleted\"");
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"createdBy\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }

    /**
     * [Red] create 接口响应不应泄漏 entity 内部字段.
     */
    @Test
    void create_shouldNotExposeEntityFields() throws Exception {
        String body = mockMvc.perform(post("/api/perf/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq("TEST_METRIC_DTO_CREATE_A"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 断言 entity 内部字段不存在
        assertThat(body).doesNotContain("\"deleted\"");
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"createdBy\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }

    /**
     * [Red] update 接口响应不应泄漏 entity 内部字段.
     */
    @Test
    void update_shouldNotExposeEntityFields() throws Exception {
        metricDefMapper.insert(metric("DTO_UPDATE_A", 1, 93));

        UpdateMetricReqDTO req = new UpdateMetricReqDTO();
        req.setMetricName("updated-dto-test");
        req.setMetricDesc("desc");
        req.setCalcFreq("DAY");
        req.setCalcMode("AUTO");
        req.setCalcLogicType("SQL");
        req.setSqlText("SELECT 3");
        req.setRefMetricCodes("[]");

        String body = mockMvc.perform(put("/api/perf/metrics/{metricCode}", "TEST_METRIC_DTO_UPDATE_A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 断言 entity 内部字段不存在
        assertThat(body).doesNotContain("\"deleted\"");
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"createdBy\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }

    private static CreateMetricReqDTO createReq(String metricCode) {
        CreateMetricReqDTO req = new CreateMetricReqDTO();
        req.setMetricCode(metricCode);
        req.setMetricName("metric-name");
        req.setMetricNameEn("metric-name-en");
        req.setMetricDesc("metric-desc");
        req.setBaseDim("EMP");
        req.setMetricLevel(1);
        req.setCalcFreq("DAY");
        req.setCalcMode("AUTO");
        req.setCalcLogicType("SQL");
        req.setSqlText("SELECT 1");
        req.setSummaryRule("SUM");
        req.setRefMetricCodes("[]");
        return req;
    }

    private static PerfMetricDef metric(String codeSuffix, int level) {
        return metric(codeSuffix, level, level);
    }

    private static PerfMetricDef metric(String codeSuffix, int level, int slot) {
        PerfMetricDef def = level > 1
                ? MetricTestDataBuilder.l2Emp(codeSuffix, slot, "[]")
                : MetricTestDataBuilder.l1Emp(codeSuffix, slot);
        def.setMetricCode("TEST_METRIC_" + codeSuffix);
        def.setMetricName("Metric-" + codeSuffix);
        def.setMetricNameEn("metric-" + codeSuffix.toLowerCase());
        def.setMetricDesc("desc-" + codeSuffix);
        def.setMetricLevel(level);
        def.setValSlot(slot);
        if (level == 1) {
            def.setRefMetricCodes("[]");
        }
        return def;
    }
}
