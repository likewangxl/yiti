package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.MetricExecuteReqDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.CascadeRefresher;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MetricDefController.execute 端点 IT（Task P3.2 Red）.
 *
 * <p>端点：{@code POST /api/perf/metrics/{metricCode}/execute}（03 §A.6）
 *
 * <p>入参：{@link MetricExecuteReqDTO}（dataDate 必填，cascade 可选默认 true，async 可选默认 true）
 * <p>出参：{@code { taskId, status }}
 *
 * <p>覆盖点：
 * <ul>
 *   <li>cascade=false → 调 MetricCalcService.calcMetric，不调 CascadeRefresher</li>
 *   <li>cascade=true （默认） → 调 CascadeRefresher.refreshCascade 并返回根 taskId</li>
 *   <li>指标不存在 → PERF-40001</li>
 *   <li>缺 reason → 400（高危审计要求 reasonRequired=true）</li>
 *   <li>缺 dataDate → 400</li>
 *   <li>@BizAuth(bizType=PERF_CONFIG, action=EXECUTE) + @AuditLog(action="METRIC_EXECUTE",
 *       resourceType="PERF_METRIC_RUN", reasonRequired=true)</li>
 * </ul>
 */
class MetricExecuteControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.MetricDefController";

    @Autowired
    private PerfMetricDefMapper metricDefMapper;

    @Autowired
    private PerfRunTaskMapper runTaskMapper;

    @MockBean
    private MetricCalcService metricCalcService;

    @MockBean
    private CascadeRefresher cascadeRefresher;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void execute_whenCascadeFalse_callsMetricCalcOnly_returnsTaskId() throws Exception {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("EXEC_NO_CASCADE", 81);
        def.setMetricCode("TEST_METRIC_EXEC_NO_CASCADE");
        def.setSqlText("SELECT 'E1' AS base_key, 1 AS metric_value");
        metricDefMapper.insert(def);

        when(metricCalcService.calcMetric(eq("TEST_METRIC_EXEC_NO_CASCADE"),
                any(LocalDate.class), any()))
                .thenReturn("TASK_EXEC_0001");

        MetricExecuteReqDTO req = new MetricExecuteReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));
        req.setCascade(false);
        req.setReason("补跑昨日缺失数据");

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/execute", "TEST_METRIC_EXEC_NO_CASCADE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value("TASK_EXEC_0001"))
                .andExpect(jsonPath("$.data.status").exists());

        verify(metricCalcService).calcMetric(eq("TEST_METRIC_EXEC_NO_CASCADE"),
                eq(LocalDate.of(2026, 4, 20)), any());
        verify(cascadeRefresher, never()).refreshCascade(any(), any(), any());
    }

    @Test
    void execute_whenCascadeTrue_callsCascadeRefresher_returnsTaskId() throws Exception {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("EXEC_CASCADE", 82);
        def.setMetricCode("TEST_METRIC_EXEC_CASCADE");
        def.setSqlText("SELECT 'E1' AS base_key, 1 AS metric_value");
        metricDefMapper.insert(def);

        when(cascadeRefresher.refreshCascade(eq("TEST_METRIC_EXEC_CASCADE"),
                any(LocalDate.class), any()))
                .thenReturn("ROOT_TASK_0002");

        MetricExecuteReqDTO req = new MetricExecuteReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));
        req.setCascade(true);
        req.setReason("级联刷新根指标");

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/execute", "TEST_METRIC_EXEC_CASCADE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value("ROOT_TASK_0002"));

        verify(cascadeRefresher).refreshCascade(eq("TEST_METRIC_EXEC_CASCADE"),
                eq(LocalDate.of(2026, 4, 20)), any());
        verify(metricCalcService, never()).calcMetric(any(), any(), any());
    }

    @Test
    void execute_whenMetricNotFound_returnsPerf40001() throws Exception {
        MetricExecuteReqDTO req = new MetricExecuteReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));
        req.setCascade(false);
        req.setReason("nonexistent");

        // MetricCalcService will not be called when metric definition is absent (pre-check)
        // —— the Controller pre-loads metric to validate, throwing PERF-40001 before calling services.
        mockMvc.perform(post("/api/perf/metrics/{metricCode}/execute", "TEST_METRIC_EXEC_MISSING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40001"));
    }

    @Test
    void execute_whenReasonMissing_returns400() throws Exception {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("EXEC_NO_REASON", 83);
        def.setMetricCode("TEST_METRIC_EXEC_NO_REASON");
        def.setSqlText("SELECT 'x' AS base_key, 1 AS metric_value");
        metricDefMapper.insert(def);

        MetricExecuteReqDTO req = new MetricExecuteReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));
        // reason missing -> should fail validation
        mockMvc.perform(post("/api/perf/metrics/{metricCode}/execute", "TEST_METRIC_EXEC_NO_REASON")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void execute_whenDataDateMissing_returns400() throws Exception {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("EXEC_NO_DATE", 84);
        def.setMetricCode("TEST_METRIC_EXEC_NO_DATE");
        def.setSqlText("SELECT 'x' AS base_key, 1 AS metric_value");
        metricDefMapper.insert(def);

        MetricExecuteReqDTO req = new MetricExecuteReqDTO();
        req.setReason("missing date");
        mockMvc.perform(post("/api/perf/metrics/{metricCode}/execute", "TEST_METRIC_EXEC_NO_DATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void execute_shouldDeclareBizAuthAndAuditLog() throws Exception {
        Class<?> controller = Class.forName(CONTROLLER_FQCN);
        Method method = controller.getDeclaredMethod("execute", String.class, MetricExecuteReqDTO.class);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(BizAction.EXECUTE);

        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.action()).isEqualTo("METRIC_EXECUTE");
        assertThat(auditLog.resourceType()).isEqualTo("PERF_METRIC_RUN");
        assertThat(auditLog.reasonRequired()).isTrue();
    }
}
