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

    /**
     * V1.3 R3.3：execute 返回 RunTaskInfoDTO（回归 03 §A.6 契约），不再返回 Map.
     *
     * <p>V1.1 P3 临时使用 {@code Map<String, Object>} 承载 taskId/status/metricCode，
     * 但 03 §A.6 契约要求结构化的 RunTaskInfoDTO，包含 taskId/status/metricCode/dataDate/version 5 字段。
     * V1.3 R3.3 新建 DTO 类并回归契约。
     *
     * <p>覆盖点：
     * <ul>
     *   <li>data 节点是结构化对象（非散列 Map）</li>
     *   <li>包含 DTO 定义的 5 个字段：taskId / status / metricCode / dataDate / version</li>
     * </ul>
     */
    @Test
    void execute_returnsRunTaskInfoDTO_withDataDateAndVersion() throws Exception {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("EXEC_DTO", 85);
        def.setMetricCode("TEST_METRIC_EXEC_DTO");
        def.setSqlText("SELECT 'E1' AS base_key, 1 AS metric_value");
        metricDefMapper.insert(def);

        // 预落一条 run_task 用于 Controller 读取状态（避免 Service 真实执行的不确定性）
        String preTaskId = "TASK_DTO_0003_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        com.bank.branch.platform.performance.entity.PerfRunTask task =
                new com.bank.branch.platform.performance.entity.PerfRunTask();
        task.setId(preTaskId);
        task.setTaskType("METRIC_RUN");
        task.setTaskKey("TEST_METRIC_EXEC_DTO");
        task.setDataDate(LocalDate.of(2026, 4, 20));
        task.setDataVersion("v_default");
        task.setStatus("RUNNING");
        task.setStartedBy("TEST_USER");
        task.setStartTime(java.time.LocalDateTime.now());
        runTaskMapper.insert(task);

        when(cascadeRefresher.refreshCascade(eq("TEST_METRIC_EXEC_DTO"),
                any(LocalDate.class), any()))
                .thenReturn(preTaskId);

        MetricExecuteReqDTO req = new MetricExecuteReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));
        req.setCascade(true);
        req.setReason("验证 DTO 契约");

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/execute", "TEST_METRIC_EXEC_DTO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value(preTaskId))
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.metricCode").value("TEST_METRIC_EXEC_DTO"))
                // V1.3 R3.3 新增：DTO 结构化契约字段
                .andExpect(jsonPath("$.data.dataDate").value("2026-04-20"))
                .andExpect(jsonPath("$.data.version").exists());
    }

    /**
     * V1.3 R3.3：Controller 方法签名应为 ResponseWrapper&lt;RunTaskInfoDTO&gt;，不再是 ResponseWrapper&lt;Map&lt;...&gt;&gt;.
     */
    @Test
    void execute_returnTypeIsRunTaskInfoDTO_notMap() throws Exception {
        Class<?> controller = Class.forName(CONTROLLER_FQCN);
        Method method = controller.getDeclaredMethod("execute", String.class, MetricExecuteReqDTO.class);
        java.lang.reflect.Type genericReturn = method.getGenericReturnType();
        // ResponseWrapper<RunTaskInfoDTO>
        assertThat(genericReturn.toString())
                .as("execute 方法返回类型应为 ResponseWrapper<RunTaskInfoDTO>，不应为 Map")
                .contains("RunTaskInfoDTO")
                .doesNotContain("java.util.Map");
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
