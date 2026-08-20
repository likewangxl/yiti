package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.event.MetricCalcCompletedEvent;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import com.bank.branch.platform.performance.service.engine.StatShowSqlRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricCalcService 多主体计算场景测试（V1.7 Task 15 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>100 主体全部成功 → SUCCESS</li>
 *   <li>5 主体失败 95 成功 → PARTIAL_FAILED</li>
 *   <li>100 主体全部失败 → FAILED</li>
 *   <li>空主体集合 → SUCCESS（跳过计算）</li>
 *   <li>failedSamples 截断至 10 条</li>
 * </ul>
 */
class MetricCalcServiceMultiSubjectTest {

    private MetricDefService metricDefService;
    private SqlExecutor sqlExecutor;
    private GroovyExecutor groovyExecutor;
    private EmpIndexResultMapper empMapper;
    private OrgIndexResultMapper orgMapper;
    private CustIndexResultMapper custMapper;
    private PerfRunTaskMapper runTaskMapper;
    private SubjectFetcher subjectFetcher;
    private ApplicationEventPublisher eventPublisher;
    private MetricCalcService service;

    @BeforeEach
    void setup() {
        metricDefService = mock(MetricDefService.class);
        sqlExecutor = mock(SqlExecutor.class);
        groovyExecutor = mock(GroovyExecutor.class);
        empMapper = mock(EmpIndexResultMapper.class);
        orgMapper = mock(OrgIndexResultMapper.class);
        custMapper = mock(CustIndexResultMapper.class);
        runTaskMapper = mock(PerfRunTaskMapper.class);
        subjectFetcher = mock(SubjectFetcher.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new MetricCalcService(metricDefService, sqlExecutor, new StatShowSqlRouter(),
            groovyExecutor, empMapper, orgMapper, custMapper, runTaskMapper,
            null, subjectFetcher, eventPublisher, null);
    }

    @Test
    @DisplayName("100 主体全部成功 → runStatus=SUCCESS, subjectSuccess=100")
    void all_100_success_status_SUCCESS() {
        PerfMetricDef def = exprDef("M_A");
        when(metricDefService.getByCodeOrNull("M_A")).thenReturn(def);
        List<String> subjects = IntStream.range(0, 100).mapToObj(i -> "E" + i).toList();
        // V1.13+：EXPR/GROOVY 主体集合改从宽表按 (dataDate, version) 现取（废弃 subject_sql/SubjectFetcher）
        when(empMapper.selectDistinctEmpIds(LocalDate.of(2026, 4, 30), "v1")).thenReturn(subjects);
        when(groovyExecutor.execute(anyString(), anyMap(), any(Duration.class)))
            .thenReturn(new BigDecimal("100"));

        service.calcMetric("M_A", LocalDate.of(2026, 4, 30), "v1");

        ArgumentCaptor<MetricCalcCompletedEvent> ev = ArgumentCaptor.forClass(MetricCalcCompletedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().runStatus()).isEqualTo("SUCCESS");
        assertThat(ev.getValue().subjectSuccess()).isEqualTo(100);
        assertThat(ev.getValue().subjectFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("5 主体失败 95 成功 → runStatus=PARTIAL_FAILED")
    void five_failed_status_PARTIAL_FAILED() {
        PerfMetricDef def = exprDef("M_A");
        when(metricDefService.getByCodeOrNull("M_A")).thenReturn(def);
        List<String> subjects = IntStream.range(0, 100).mapToObj(i -> "E" + i).toList();
        // V1.13+：EXPR/GROOVY 主体集合改从宽表按 (dataDate, version) 现取（废弃 subject_sql/SubjectFetcher）
        when(empMapper.selectDistinctEmpIds(LocalDate.of(2026, 4, 30), "v1")).thenReturn(subjects);
        AtomicInteger callCount = new AtomicInteger();
        when(groovyExecutor.execute(anyString(), anyMap(), any(Duration.class)))
            .thenAnswer(inv -> {
                int n = callCount.incrementAndGet();
                if (n <= 5) {
                    throw new ArithmeticException("div by zero");
                }
                return new BigDecimal("100");
            });

        service.calcMetric("M_A", LocalDate.of(2026, 4, 30), "v1");

        ArgumentCaptor<MetricCalcCompletedEvent> ev = ArgumentCaptor.forClass(MetricCalcCompletedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().runStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(ev.getValue().subjectSuccess()).isEqualTo(95);
        assertThat(ev.getValue().subjectFailed()).isEqualTo(5);
    }

    @Test
    @DisplayName("100 主体全部失败 → runStatus=FAILED")
    void all_100_failed_status_FAILED() {
        PerfMetricDef def = exprDef("M_A");
        when(metricDefService.getByCodeOrNull("M_A")).thenReturn(def);
        List<String> subjects = IntStream.range(0, 100).mapToObj(i -> "E" + i).toList();
        // V1.13+：EXPR/GROOVY 主体集合改从宽表按 (dataDate, version) 现取（废弃 subject_sql/SubjectFetcher）
        when(empMapper.selectDistinctEmpIds(LocalDate.of(2026, 4, 30), "v1")).thenReturn(subjects);
        when(groovyExecutor.execute(anyString(), anyMap(), any(Duration.class)))
            .thenThrow(new ArithmeticException("div by zero"));

        service.calcMetric("M_A", LocalDate.of(2026, 4, 30), "v1");

        ArgumentCaptor<MetricCalcCompletedEvent> ev = ArgumentCaptor.forClass(MetricCalcCompletedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().runStatus()).isEqualTo("FAILED");
        assertThat(ev.getValue().subjectSuccess()).isEqualTo(0);
        assertThat(ev.getValue().subjectFailed()).isEqualTo(100);
    }

    @Test
    @DisplayName("空主体集合 → runStatus=SUCCESS, subjectTotal=0（跳过计算）")
    void empty_subject_set_status_SUCCESS_skip() {
        PerfMetricDef def = exprDef("M_A");
        when(metricDefService.getByCodeOrNull("M_A")).thenReturn(def);
        when(empMapper.selectDistinctEmpIds(LocalDate.of(2026, 4, 30), "v1")).thenReturn(List.of());

        service.calcMetric("M_A", LocalDate.of(2026, 4, 30), "v1");

        ArgumentCaptor<MetricCalcCompletedEvent> ev = ArgumentCaptor.forClass(MetricCalcCompletedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().runStatus()).isEqualTo("SUCCESS");
        assertThat(ev.getValue().subjectTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("50 主体全失败 → failedSamples 截断至 10 条")
    void failedSamples_truncated_to_10() {
        PerfMetricDef def = exprDef("M_A");
        when(metricDefService.getByCodeOrNull("M_A")).thenReturn(def);
        List<String> subjects = IntStream.range(0, 50).mapToObj(i -> "E" + i).toList();
        // V1.13+：EXPR/GROOVY 主体集合改从宽表按 (dataDate, version) 现取（废弃 subject_sql/SubjectFetcher）
        when(empMapper.selectDistinctEmpIds(LocalDate.of(2026, 4, 30), "v1")).thenReturn(subjects);
        when(groovyExecutor.execute(anyString(), anyMap(), any(Duration.class)))
            .thenThrow(new ArithmeticException("err"));

        service.calcMetric("M_A", LocalDate.of(2026, 4, 30), "v1");

        // 验证 perf_run_task params_json 中 failedSamples 长度 <= 10
        ArgumentCaptor<String> paramsCaptor = ArgumentCaptor.forClass(String.class);
        verify(runTaskMapper).updateStatusWithParams(anyString(), eq("FAILED"), any(),
            paramsCaptor.capture());
        String json = paramsCaptor.getValue();
        // 包含校验：json 应含 "failedSamples" 字段
        assertThat(json).contains("failedSamples");
        // V1.7：用 Jackson 精确解析 failedSamples 数组长度，不依赖字母计数
        // （新增 jobKey 字段 "PERF_METRIC_M_A" 含字母 E，字母计数法失效）
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            int samplesSize = root.path("failedSamples").size();
            assertThat(samplesSize).isLessThanOrEqualTo(10);
        } catch (Exception e) {
            throw new AssertionError("params_json 解析失败: " + json, e);
        }
    }

    @Test
    @DisplayName("EXPR 引用含取值时间后缀：按 DateMacroResolver 目标日期读宽表并以完整 token 绑定")
    void calcMetric_exprWithValueTimeSuffix_bindsHistoricalValues() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("TEST_METRIC_DELTA");
        def.setBaseDim("EMP");
        def.setCalcLogicType("EXPR");
        def.setValSlot(5);
        def.setExprText("M_A - M_A__D1");
        def.setDeleted(0);
        when(metricDefService.getByCodeOrNull("TEST_METRIC_DELTA")).thenReturn(def);

        LocalDate dataDate = LocalDate.of(2026, 7, 9);
        String version = "V1";
        when(empMapper.selectDistinctEmpIds(dataDate, version)).thenReturn(List.of("E001"));
        when(empMapper.selectValSlotsByCodes(anyList())).thenReturn(Map.of("M_A", 3));
        when(empMapper.selectValBySlot("E001", 3, dataDate, version)).thenReturn(new BigDecimal("100"));
        when(empMapper.selectValBySlot("E001", 3, LocalDate.of(2026, 7, 8), version)).thenReturn(new BigDecimal("30"));
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        when(groovyExecutor.execute(eq("M_A - M_A__D1"), varsCaptor.capture(), any(Duration.class)))
            .thenReturn(new BigDecimal("70"));

        service.calcMetric("TEST_METRIC_DELTA", dataDate, version);

        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("M_A", new BigDecimal("100"));
        assertThat(vars).containsEntry("M_A__D1", new BigDecimal("30"));
        verify(empMapper).insertSlotValue("E001", dataDate, version, 5, new BigDecimal("70"));
    }

    private PerfMetricDef exprDef(String code) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setCalcLogicType("EXPR");
        def.setExprText("a + b * 0.3");
        def.setBaseDim("EMP");
        def.setSubjectSql("SELECT emp_id FROM t");
        def.setValSlot(1);
        def.setDeleted(0);
        def.setRefMetricCodes("[]");
        return def;
    }
}
