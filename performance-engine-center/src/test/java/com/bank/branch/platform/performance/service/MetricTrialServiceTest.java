package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.dto.MetricTrialResult;
import com.bank.branch.platform.performance.service.engine.DateMacroResolver;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricTrialService 单元测试（Task P3.1 Red）.
 *
 * <p>职责覆盖：
 * <ul>
 *   <li>SQL 指标：调 SqlExecutor → 返回样本（不超过 sampleSize）+ totalRows + 耗时</li>
 *   <li>EXPR 指标：调 GroovyExecutor → 返回 exprResult 单值</li>
 *   <li>指标不存在 / 已软删除：抛 METRIC_NOT_FOUND</li>
 *   <li>SQL 执行异常：透传 PerfException（不写宽表 / 不写 run_task）</li>
 *   <li>sampleSize 默认 20，上限 100（超出取 100）</li>
 *   <li><strong>不写宽表 / 不写 run_task</strong>：与 MetricCalcService 显著区别</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MetricTrialServiceTest {

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private SqlExecutor sqlExecutor;

    @Mock
    private GroovyExecutor groovyExecutor;

    @Mock
    private PerfEngineProperties perfEngineProperties;

    @Mock
    private MetricCalcService metricCalcService;

    @InjectMocks
    private MetricTrialService metricTrialService;

    @Test
    @DisplayName("SQL 试运行：返回 baseKey->value 样本，包含 totalRows / executionMillis，不调任何宽表")
    void trial_sqlMetric_returnsSamples() {
        PerfMetricDef def = buildSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_SQL_01")).thenReturn(def);
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        Map<String, BigDecimal> sqlOut = new LinkedHashMap<>();
        sqlOut.put("E001", new BigDecimal("10.00"));
        sqlOut.put("E002", new BigDecimal("20.00"));
        sqlOut.put("E003", new BigDecimal("30.00"));
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(sqlOut);

        MetricTrialResult result = metricTrialService.trial(
                "TEST_TRIAL_SQL_01", LocalDate.of(2026, 4, 20), 2, null);

        assertThat(result).isNotNull();
        assertThat(result.getSampleSize()).isEqualTo(2);
        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSamples()).hasSize(2);
        assertThat(result.getSamples().get(0).get("baseKey")).isEqualTo("E001");
        assertThat(result.getSamples().get(0).get("metricValue")).isEqualTo(new BigDecimal("10.00"));
        assertThat(result.getExprResult()).isNull();
        assertThat(result.getExecutionMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("EXPR 试运行：返回 exprResult 单值，samples 为空")
    void trial_exprMetric_returnsSingleValue() {
        PerfMetricDef def = buildExprMetric();
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_EXPR_01")).thenReturn(def);
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        when(groovyExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(new BigDecimal("42"));

        MetricTrialResult result = metricTrialService.trial(
                "TEST_TRIAL_EXPR_01", LocalDate.of(2026, 4, 20), 20, null);

        assertThat(result.getExprResult()).isEqualByComparingTo("42");
        assertThat(result.getSamples()).isNullOrEmpty();
        assertThat(result.getSampleSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("指标不存在：抛 METRIC_NOT_FOUND，不调 Executor")
    void trial_whenMetricNotFound_throws() {
        when(metricDefService.getByCodeOrNull("NONE")).thenReturn(null);

        assertThatThrownBy(() -> metricTrialService.trial("NONE", LocalDate.now(), 20, null))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode").isEqualTo(PerfErrorCode.METRIC_NOT_FOUND);

        verify(sqlExecutor, never()).execute(anyString(), anyMap(), any(Duration.class));
        verify(groovyExecutor, never()).execute(anyString(), anyMap(), any(Duration.class));
    }

    @Test
    @DisplayName("已软删除指标：抛 METRIC_NOT_FOUND")
    void trial_softDeleted_throwsNotFound() {
        PerfMetricDef def = buildSqlMetric();
        def.setDeleted(1);
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_DEL")).thenReturn(def);

        assertThatThrownBy(() -> metricTrialService.trial("TEST_TRIAL_DEL", LocalDate.now(), 20, null))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode").isEqualTo(PerfErrorCode.METRIC_NOT_FOUND);
    }

    @Test
    @DisplayName("SQL 执行异常：透传 PerfException（不被吞掉，不捕获转 FAILED）")
    void trial_whenSqlFails_throwsDirectly() {
        PerfMetricDef def = buildSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_ERR")).thenReturn(def);
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        PerfException boom = new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "禁止 DML");
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenThrow(boom);

        assertThatThrownBy(() -> metricTrialService.trial("TEST_TRIAL_ERR", LocalDate.now(), 20, null))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode").isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("sampleSize 为 null 时取默认 20；超过 100 收敛到 100")
    void trial_sampleSizeFallback_andCap() {
        PerfMetricDef def = buildSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_CAP")).thenReturn(def);
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        // 构造 150 行样本：验证 sampleSize=200 请求被收敛到 100
        Map<String, BigDecimal> big = new LinkedHashMap<>();
        for (int i = 0; i < 150; i++) {
            big.put("E" + String.format("%03d", i), new BigDecimal(i));
        }
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(big);

        MetricTrialResult capped = metricTrialService.trial(
                "TEST_TRIAL_CAP", LocalDate.of(2026, 4, 20), 200, null);
        assertThat(capped.getSampleSize()).isEqualTo(100);
        assertThat(capped.getTotalRows()).isEqualTo(150);

        // sampleSize=null 时使用默认 20
        MetricTrialResult defaulted = metricTrialService.trial(
                "TEST_TRIAL_CAP", LocalDate.of(2026, 4, 20), null, null);
        assertThat(defaulted.getSampleSize()).isEqualTo(20);
    }

    @Test
    void runSql_userParamsCannotOverrideSystemMacros() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TRIAL_MACRO");
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1 AS base_key, 2 AS metric_value WHERE :dateToday IS NOT NULL");
        when(metricDefService.getByCodeOrNull("M_TRIAL_MACRO")).thenReturn(def);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(Map.of());

        LocalDate dataDate = LocalDate.of(2026, 5, 20);
        Map<String, Object> userParams = new HashMap<>();
        userParams.put("dateToday", LocalDate.of(2099, 1, 1));
        userParams.put("dateMonthEnd", LocalDate.of(2099, 12, 31));
        userParams.put("customParam", "kept");

        metricTrialService.trial("M_TRIAL_MACRO", dataDate, 10, userParams);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.captor();
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        Map<String, Object> sent = cap.getValue();
        DateMacroResolver.resolve(dataDate)
                .forEach((k, v) -> assertThat(sent).containsEntry(k, v));
        assertThat(sent).containsEntry("customParam", "kept");
    }

    @Test
    void runSql_nullDataDate_doesNotInjectMacros() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TRIAL_NULL_DATE");
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1 AS base_key, 2 AS metric_value");
        when(metricDefService.getByCodeOrNull("M_TRIAL_NULL_DATE")).thenReturn(def);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(Map.of());

        metricTrialService.trial("M_TRIAL_NULL_DATE", null, 10, null);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.captor();
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        Map<String, Object> sent = cap.getValue();
        assertThat(sent).doesNotContainKeys(
                "dateToday", "dateYesterday",
                "dateMonthEnd", "datePrevMonthEnd",
                "dateQuarterEnd", "datePrevQuarterEnd",
                "dateYearEnd", "datePrevYearEnd");
    }

    @Test
    @DisplayName("业绩分配日期：trial 显式传 allocDate → params.allocDate 等于该日期")
    void trial_explicitAllocDate_boundIntoParams() {
        PerfMetricDef def = buildSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_ALLOC_01")).thenReturn(def);
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(Map.of());

        LocalDate dataDate = LocalDate.of(2026, 6, 17);
        LocalDate allocDate = LocalDate.of(2026, 6, 10);
        metricTrialService.trial("TEST_TRIAL_ALLOC_01", dataDate, 20, null, allocDate);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.captor();
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        assertThat(cap.getValue()).containsEntry("allocDate", allocDate);
    }

    @Test
    @DisplayName("业绩分配日期：trial allocDate=null → 兜底等于 dataDate")
    void trial_nullAllocDate_defaultsToDataDate() {
        PerfMetricDef def = buildSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_TRIAL_ALLOC_02")).thenReturn(def);
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(Map.of());

        LocalDate dataDate = LocalDate.of(2026, 6, 17);
        metricTrialService.trial("TEST_TRIAL_ALLOC_02", dataDate, 20, null, null);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.captor();
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        assertThat(cap.getValue()).containsEntry("allocDate", dataDate);
    }

    @Test
    @DisplayName("业绩分配日期：trialAdhoc 显式传 allocDate → params.allocDate 等于该日期")
    void trialAdhoc_explicitAllocDate_boundIntoParams() {
        when(perfEngineProperties.getSqlTimeoutSeconds()).thenReturn(30);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(Map.of());

        LocalDate dataDate = LocalDate.of(2026, 6, 17);
        LocalDate allocDate = LocalDate.of(2026, 6, 10);
        metricTrialService.trialAdhoc("SQL", "EMP",
                "SELECT 1 AS base_key, 2 AS metric_value WHERE :allocDate IS NOT NULL",
                null, dataDate, 20, null, allocDate);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.captor();
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        assertThat(cap.getValue()).containsEntry("allocDate", allocDate);
    }

    // ========== 测试构造器 ==========

    private PerfMetricDef buildSqlMetric() {
        PerfMetricDef def = new PerfMetricDef();
        def.setId("TRIAL_EMP_01");
        def.setMetricCode("TEST_TRIAL_SQL_01");
        def.setBaseDim("EMP");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT emp_id AS base_key, amt AS metric_value FROM t");
        def.setValSlot(1);
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        return def;
    }

    private PerfMetricDef buildExprMetric() {
        PerfMetricDef def = new PerfMetricDef();
        def.setId("TRIAL_ORG_01");
        def.setMetricCode("TEST_TRIAL_EXPR_01");
        def.setBaseDim("ORG");
        def.setCalcLogicType("EXPR");
        def.setExprText("a + b");
        def.setValSlot(2);
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        return def;
    }
}
