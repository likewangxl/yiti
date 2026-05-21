package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricCalcService 单元测试（Task P2.4 Red）.
 *
 * <p>场景覆盖：
 * <ul>
 *   <li>SQL 类指标：路由 SqlValidator + SqlExecutor → 写 EMP 宽表 + 成功态 run_task</li>
 *   <li>EXPR 类指标：Groovy 执行 + 写 ORG 宽表</li>
 *   <li>指标不存在：抛 METRIC_NOT_FOUND 且不插入 run_task</li>
 *   <li>sqlText 为空且 SQL 类型：抛 METRIC_CALC_LOGIC_INVALID + 写 FAILED run_task</li>
 *   <li>软删除（deleted=1）指标：抛 METRIC_NOT_FOUND</li>
 *   <li>PROC / SUMMARY 暂未支持：抛 CALC_JOB_FAILED</li>
 *   <li>未分配 slot：抛 METRIC_CALC_LOGIC_INVALID</li>
 *   <li>baseDim=CUST：路由 CustIndexResultMapper.insertSlotValue</li>
 *   <li>SQL 执行失败：run_task 标记 FAILED 并记录错误信息</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MetricCalcServiceTest {

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private SqlExecutor sqlExecutor;

    @Mock
    private GroovyExecutor groovyExecutor;

    @Mock
    private EmpIndexResultMapper empIndexResultMapper;

    @Mock
    private OrgIndexResultMapper orgIndexResultMapper;

    @Mock
    private CustIndexResultMapper custIndexResultMapper;

    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;

    @Mock
    private SubjectFetcher subjectFetcher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MetricCalcService metricCalcService;

    @BeforeEach
    void configureDefaults() {
        // 测试 ExecutorService 超时的默认值不依赖 PerfEngineProperties，这里省略配置注入
    }

    @Test
    @DisplayName("SQL 指标 + baseDim=EMP：调 SqlExecutor + 批量 UPSERT + run_task 标记 SUCCESS")
    void calcMetric_sqlOnEmp_writesWideTableAndMarksSuccess() {
        PerfMetricDef def = buildEmpSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_CALC_EMP_01")).thenReturn(def);
        Map<String, BigDecimal> execResult = new LinkedHashMap<>();
        execResult.put("E001", new BigDecimal("10"));
        execResult.put("E002", new BigDecimal("20"));
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(execResult);

        String taskId = metricCalcService.calcMetric("TEST_CALC_EMP_01", LocalDate.of(2026, 4, 22), "20260422");

        assertThat(taskId).isNotBlank();
        verify(sqlExecutor, times(1)).execute(eq("SELECT id AS base_key, v AS metric_value FROM t"), anyMap(), any(Duration.class));
        verify(empIndexResultMapper).insertSlotValue(eq("E001"), eq(LocalDate.of(2026, 4, 22)), eq("20260422"), eq(1), eq(new BigDecimal("10")));
        verify(empIndexResultMapper).insertSlotValue(eq("E002"), eq(LocalDate.of(2026, 4, 22)), eq("20260422"), eq(1), eq(new BigDecimal("20")));

        ArgumentCaptor<PerfRunTask> captor = ArgumentCaptor.forClass(PerfRunTask.class);
        // 至少一次 insert（PENDING） + updateStatus(RUNNING) + updateStatusWithParams(SUCCESS)
        verify(perfRunTaskMapper).insert(captor.capture());
        verify(perfRunTaskMapper).updateStatus(eq(taskId), eq("RUNNING"), eq(null));
        verify(perfRunTaskMapper).updateStatusWithParams(eq(taskId), eq("SUCCESS"), eq(null), anyString());
        verify(orgIndexResultMapper, never()).insertSlotValue(anyString(), any(LocalDate.class), anyString(), any(Integer.class), any(BigDecimal.class));
        verify(custIndexResultMapper, never()).insertSlotValue(anyString(), any(LocalDate.class), anyString(), any(Integer.class), any(BigDecimal.class));

        PerfRunTask inserted = captor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getTaskKey()).isEqualTo("TEST_CALC_EMP_01");
        assertThat(inserted.getTaskType()).isEqualTo("METRIC_RUN");
        assertThat(inserted.getDataDate()).isEqualTo(LocalDate.of(2026, 4, 22));
        assertThat(inserted.getDataVersion()).isEqualTo("20260422");
    }

    @Test
    @DisplayName("EXPR 指标 + baseDim=ORG：调 GroovyExecutor 并写 ORG 宽表（V1.7 多主体）")
    void calcMetric_exprOnOrg_writesOrgWideTable() {
        PerfMetricDef def = buildOrgExprMetric();
        when(metricDefService.getByCodeOrNull("TEST_CALC_ORG_01")).thenReturn(def);
        // V1.7 EXPR 多主体路径：subjectFetcher 返回一个主体
        when(subjectFetcher.fetch(anyString(), anyMap())).thenReturn(List.of("O001"));
        when(groovyExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(new BigDecimal("42"));

        String taskId = metricCalcService.calcMetric("TEST_CALC_ORG_01", LocalDate.of(2026, 4, 22), "20260422");

        assertThat(taskId).isNotBlank();
        verify(groovyExecutor).execute(eq("a + b"), anyMap(), any(Duration.class));
        verify(orgIndexResultMapper).insertSlotValue(eq("O001"), eq(LocalDate.of(2026, 4, 22)), eq("20260422"), eq(2), eq(new BigDecimal("42")));
        verify(empIndexResultMapper, never()).insertSlotValue(anyString(), any(), anyString(), any(), any());
        // V1.7 改用 updateStatusWithParams，SUCCESS 状态
        verify(perfRunTaskMapper).updateStatusWithParams(eq(taskId), eq("SUCCESS"), eq(null), anyString());
    }

    @Test
    @DisplayName("指标不存在：抛 METRIC_NOT_FOUND，不插入 run_task")
    void calcMetric_whenMetricNotFound_throws() {
        when(metricDefService.getByCodeOrNull("NONE")).thenReturn(null);

        assertThatThrownBy(() -> metricCalcService.calcMetric("NONE", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_NOT_FOUND);

        verify(perfRunTaskMapper, never()).insert(any(PerfRunTask.class));
    }

    @Test
    @DisplayName("已软删除指标：抛 METRIC_NOT_FOUND")
    void calcMetric_softDeleted_throwsNotFound() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setDeleted(1);
        when(metricDefService.getByCodeOrNull("TEST_CALC_DEL")).thenReturn(def);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_DEL", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_NOT_FOUND);
    }

    @Test
    @DisplayName("SQL 类型指标但 sqlText 为空：标记 FAILED + 抛 METRIC_CALC_LOGIC_INVALID")
    void calcMetric_sqlMetric_withoutSqlText_failsAndMarksFailed() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setSqlText(null);
        when(metricDefService.getByCodeOrNull("TEST_CALC_EMPTY_SQL")).thenReturn(def);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_EMPTY_SQL", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);

        verify(perfRunTaskMapper).insert(any(PerfRunTask.class));
        // FAILED 状态 + error_msg
        verify(perfRunTaskMapper).updateStatus(anyString(), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("SQL 执行异常：run_task 标记 FAILED + 错误信息落库，异常继续抛出")
    void calcMetric_whenSqlExecutionFails_marksFailedAndPropagates() {
        PerfMetricDef def = buildEmpSqlMetric();
        when(metricDefService.getByCodeOrNull("TEST_CALC_SQL_ERR")).thenReturn(def);
        PerfException boom = new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "SQL 执行失败");
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenThrow(boom);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_SQL_ERR", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class);

        verify(perfRunTaskMapper).updateStatus(anyString(), eq("FAILED"), anyString());
        verify(empIndexResultMapper, never()).insertSlotValue(anyString(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("PROC 类型指标：V1.1 P2 不支持，抛 CALC_JOB_FAILED + FAILED 状态")
    void calcMetric_procType_notSupported() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setCalcLogicType("PROC");
        when(metricDefService.getByCodeOrNull("TEST_CALC_PROC")).thenReturn(def);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_PROC", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.CALC_JOB_FAILED);
        verify(perfRunTaskMapper).updateStatus(anyString(), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("SUMMARY 类型指标：V1.1 P2 不支持，抛 CALC_JOB_FAILED + FAILED 状态")
    void calcMetric_summaryType_notSupported() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setCalcLogicType("SUMMARY");
        when(metricDefService.getByCodeOrNull("TEST_CALC_SUM")).thenReturn(def);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_SUM", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.CALC_JOB_FAILED);
    }

    @Test
    @DisplayName("valSlot 未分配：抛 METRIC_CALC_LOGIC_INVALID + FAILED 状态")
    void calcMetric_whenSlotUnallocated_fails() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setValSlot(null);
        when(metricDefService.getByCodeOrNull("TEST_CALC_NO_SLOT")).thenReturn(def);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_NO_SLOT", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
        verify(perfRunTaskMapper).updateStatus(anyString(), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("V1.12：valSlot=400 在新上界内（应通过 validateSlot，进入 SQL 执行路径）")
    void calcMetric_whenSlotIs400_passesValidation() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setValSlot(400);
        when(metricDefService.getByCodeOrNull("TEST_CALC_SLOT_400")).thenReturn(def);
        Map<String, BigDecimal> execResult = new LinkedHashMap<>();
        execResult.put("E001", new BigDecimal("9.9"));
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(execResult);

        String taskId = metricCalcService.calcMetric("TEST_CALC_SLOT_400", LocalDate.of(2026, 5, 19), "v");

        assertThat(taskId).isNotBlank();
        verify(empIndexResultMapper).insertSlotValue(eq("E001"), eq(LocalDate.of(2026, 5, 19)), eq("v"), eq(400), eq(new BigDecimal("9.9")));
    }

    @Test
    @DisplayName("V1.12：valSlot=401 越上界 → 抛 METRIC_CALC_LOGIC_INVALID + FAILED 状态")
    void calcMetric_whenSlotIs401_fails() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setValSlot(401);
        when(metricDefService.getByCodeOrNull("TEST_CALC_SLOT_401")).thenReturn(def);

        assertThatThrownBy(() -> metricCalcService.calcMetric("TEST_CALC_SLOT_401", LocalDate.now(), "v"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
        verify(perfRunTaskMapper).updateStatus(anyString(), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("SQL 指标 + baseDim=CUST：路由 CustIndexResultMapper")
    void calcMetric_sqlOnCust_writesCustWideTable() {
        PerfMetricDef def = buildEmpSqlMetric();
        def.setBaseDim("CUST");
        when(metricDefService.getByCodeOrNull("TEST_CALC_CUST_01")).thenReturn(def);
        Map<String, BigDecimal> execResult = new LinkedHashMap<>();
        execResult.put("C001", new BigDecimal("5"));
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class))).thenReturn(execResult);

        String taskId = metricCalcService.calcMetric("TEST_CALC_CUST_01", LocalDate.of(2026, 4, 22), "20260422");

        assertThat(taskId).isNotBlank();
        verify(custIndexResultMapper).insertSlotValue(eq("C001"), eq(LocalDate.of(2026, 4, 22)), eq("20260422"), eq(1), eq(new BigDecimal("5")));
        verify(empIndexResultMapper, never()).insertSlotValue(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void executeSqlAndPersist_injectsDateMacrosIntoParams() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TEST_MACRO");
        def.setBaseDim("EMP");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT emp_id AS base_key, 1 AS metric_value FROM t WHERE dt = :dateMonthEnd");
        def.setValSlot(1);
        when(metricDefService.getByCodeOrNull("M_TEST_MACRO")).thenReturn(def);

        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(java.util.Map.of());

        LocalDate dataDate = LocalDate.of(2026, 5, 20);
        metricCalcService.calcMetric("M_TEST_MACRO", dataDate, "V1", "MANUAL");

        ArgumentCaptor<java.util.Map<String, Object>> paramsCap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(sqlExecutor).execute(eq(def.getSqlText()), paramsCap.capture(), any(Duration.class));
        java.util.Map<String, Object> captured = paramsCap.getValue();
        assertThat(captured)
                .containsEntry("dataDate", dataDate)
                .containsEntry("version", "V1")
                .containsEntry("dateToday", dataDate)
                .containsEntry("dateYesterday", dataDate.minusDays(1))
                .containsEntry("dateMonthEnd", LocalDate.of(2026, 5, 31))
                .containsEntry("datePrevMonthEnd", LocalDate.of(2026, 4, 30))
                .containsEntry("dateQuarterEnd", LocalDate.of(2026, 6, 30))
                .containsEntry("datePrevQuarterEnd", LocalDate.of(2026, 3, 31))
                .containsEntry("dateYearEnd", LocalDate.of(2026, 12, 31))
                .containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
    }

    // ========== 测试构造器 ==========

    private PerfMetricDef buildEmpSqlMetric() {
        PerfMetricDef def = new PerfMetricDef();
        def.setId("TID_EMP_01");
        def.setMetricCode("TEST_CALC_EMP_01");
        def.setMetricName("EMP SQL 测试指标");
        def.setBaseDim("EMP");
        def.setMetricLevel(1);
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT id AS base_key, v AS metric_value FROM t");
        def.setValSlot(1);
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        return def;
    }

    private PerfMetricDef buildOrgExprMetric() {
        PerfMetricDef def = new PerfMetricDef();
        def.setId("TID_ORG_01");
        def.setMetricCode("TEST_CALC_ORG_01");
        def.setMetricName("ORG EXPR 测试指标");
        def.setBaseDim("ORG");
        def.setMetricLevel(2);
        def.setCalcLogicType("EXPR");
        def.setExprText("a + b");
        def.setRefMetricCodes("[\"REF_A\",\"REF_B\"]");
        // V1.7 多主体路径需要 subjectSql
        def.setSubjectSql("SELECT org_code FROM org_table");
        def.setValSlot(2);
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        return def;
    }
}
