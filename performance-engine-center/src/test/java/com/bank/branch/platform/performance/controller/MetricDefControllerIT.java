package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricRefMapper;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(MetricDefControllerIT.MethodValidationTestAdvice.class)
class MetricDefControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.MetricDefController";

    @Autowired
    private PerfMetricDefMapper metricDefMapper;

    @Autowired
    private PerfMetricRefMapper metricRefMapper;

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
        mockMvc.perform(get("/api/perf/metrics/{metricCode}", "TEST_METRIC_MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40401"));
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
    void list_whenPageNoIsZero_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/perf/metrics")
                        .param("pageNo", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenPageSizeTooLarge_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/perf/metrics")
                        .param("pageNo", "1")
                        .param("pageSize", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenPageSizeIsZero_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/perf/metrics")
                        .param("pageNo", "1")
                        .param("pageSize", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSlots_whenBaseDimBlank_returnsBadRequestAndUnifiedError() throws Exception {
        mockMvc.perform(get("/api/perf/metrics/val-slots")
                        .param("baseDim", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.code").value("VALID_001"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
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

    private void assertBizAuth(String methodName, Class<?>[] parameterTypes, BizAction action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(action);
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

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class MethodValidationTestAdvice {

        @ExceptionHandler(ConstraintViolationException.class)
        ResponseEntity<ResponseWrapper<?>> handleConstraintViolationException(
                ConstraintViolationException ex) {
            return ResponseEntity.badRequest()
                    .body(ResponseWrapper.error("VALID_001", ex.getMessage()));
        }
    }
}
