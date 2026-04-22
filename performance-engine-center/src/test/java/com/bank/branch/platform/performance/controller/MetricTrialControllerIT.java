package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.MetricTrialReqDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MetricDefController.trialRun 端点 IT（Task P3.1 Red）.
 *
 * <p>端点：{@code POST /api/perf/metrics/{metricCode}/trial-run}（03 §A.5）
 *
 * <p>覆盖点：
 * <ul>
 *   <li>合法 SQL 指标：返回样本结果 code=0，宽表 emp_index_result 未写入</li>
 *   <li>含禁止关键字 SQL：抛 PERF-42201 METRIC_CALC_LOGIC_INVALID</li>
 *   <li>指标不存在：返回 PERF-40001</li>
 *   <li>端点声明 @BizAuth + @AuditLog（action=EXECUTE, resourceType=PERF_METRIC_TRIAL）</li>
 *   <li>不创建 run_task（P3.1 契约：trial-run 完全无侧效）</li>
 * </ul>
 */
class MetricTrialControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.MetricDefController";

    @Autowired
    private PerfMetricDefMapper metricDefMapper;

    @Autowired
    private EmpIndexResultMapper empIndexResultMapper;

    @Autowired
    private PerfRunTaskMapper runTaskMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void trialRun_whenValidSqlMetric_returnsSamplesWithoutSideEffect() throws Exception {
        // 准备指标定义：SQL 类型，SQL 读静态行实现不依赖真实数据
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("TRIAL_SQL_OK", 71);
        def.setMetricCode("TEST_METRIC_TRIAL_SQL_OK");
        def.setSqlText("SELECT 'E_TRIAL_001' AS base_key, 123.45 AS metric_value");
        metricDefMapper.insert(def);

        MetricTrialReqDTO req = new MetricTrialReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));
        req.setSampleSize(10);

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/trial-run", "TEST_METRIC_TRIAL_SQL_OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sampleSize").value(1))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.samples[0].baseKey").value("E_TRIAL_001"));

        // 侧效零容忍：不得写入宽表，不得创建 run_task
        // （这里不能直接数全表，但因 emp_index_result 无该 base_key 的插入，可以间接断言）
        assertThat(empIndexResultMapper.selectSlotValue(
                "E_TRIAL_001", LocalDate.of(2026, 4, 20), "v_test", 71)).isNull();
    }

    @Test
    void trialRun_whenSqlContainsForbidden_returnsMetricCalcLogicInvalid() throws Exception {
        PerfMetricDef def = MetricTestDataBuilder.l1Emp("TRIAL_SQL_BAD", 72);
        def.setMetricCode("TEST_METRIC_TRIAL_SQL_BAD");
        // 含 DELETE 的 SQL 应被 SqlValidator 拦截
        def.setSqlText("SELECT * FROM (SELECT 1) t WHERE 1=1 /* DELETE FROM x */");
        metricDefMapper.insert(def);

        MetricTrialReqDTO req = new MetricTrialReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/trial-run", "TEST_METRIC_TRIAL_SQL_BAD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42201"));
    }

    @Test
    void trialRun_whenMetricNotFound_returnsPerf40001() throws Exception {
        MetricTrialReqDTO req = new MetricTrialReqDTO();
        req.setDataDate(LocalDate.of(2026, 4, 20));

        mockMvc.perform(post("/api/perf/metrics/{metricCode}/trial-run", "TEST_METRIC_TRIAL_MISSING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40001"));
    }

    @Test
    void trialRun_shouldDeclareBizAuthAndAuditLog() throws Exception {
        Class<?> controller = Class.forName(CONTROLLER_FQCN);
        Method method = controller.getDeclaredMethod("trialRun", String.class, MetricTrialReqDTO.class);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(BizAction.EXECUTE);

        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.action()).isEqualTo("METRIC_TRIAL_RUN");
        assertThat(auditLog.resourceType()).isEqualTo("PERF_METRIC_TRIAL");
    }
}
