package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfKpiScore;
import com.bank.branch.platform.performance.entity.PerfMetricCalcTask;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiScoreMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.mapper.SubjectSlotValueRow;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KpiScoreCalcService} 单元测试.
 *
 * <p>覆盖：前置依赖未完成 → 任务 FAILED 且不计分；happy path EMP 维度 →
 * 公式求值 + upsert + 任务 SUCCESS；周期键派生。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiScoreCalcServiceTest {

    @Mock private PerfMetricCalcTaskMapper taskMapper;
    @Mock private PerfKpiSchemeMapper schemeMapper;
    @Mock private PerfKpiItemMapper itemMapper;
    @Mock private PerfTargetPlanMapper targetPlanMapper;
    @Mock private PerfTargetValueMapper targetValueMapper;
    @Mock private PerfKpiScoreMapper scoreMapper;
    @Mock private MetricDefService metricDefService;
    @Mock private KpiScoreFormulaService formulaService;
    @Mock private EmpIndexResultMapper empIndexResultMapper;
    @Mock private OrgIndexResultMapper orgIndexResultMapper;
    @Mock private CustIndexResultMapper custIndexResultMapper;
    @Mock private com.bank.branch.platform.performance.mapper.PerfKpiCalcLogMapper kpiCalcLogMapper;
    @Mock private com.bank.branch.platform.auth.api.UserApi userApi;
    @Mock private com.bank.branch.platform.auth.api.OrgApi orgApi;
    @Mock private SqlExecutor sqlExecutor;

    @InjectMocks private KpiScoreCalcService service;

    private static final LocalDate DATA_DATE = LocalDate.of(2026, 6, 3);

    @Test
    void calculate_dataDateNull_throws() {
        assertThatThrownBy(() -> service.calculate(null, null, "MANUAL", null))
                .isInstanceOf(PerfException.class);
        verify(taskMapper, never()).insert(any(PerfMetricCalcTask.class));
    }

    @Test
    void calculate_levelNotAllDone_marksTaskFailedAndDoesNotScore() {
        // 任一级别无 SUCCESS 记录 → 前置检查失败
        when(taskMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.calculate(DATA_DATE, null, "MANUAL", null))
                .isInstanceOf(PerfException.class);

        // 已登记 RUNNING + 置 FAILED
        verify(taskMapper, times(1)).insert(any(PerfMetricCalcTask.class));
        ArgumentCaptor<PerfMetricCalcTask> cap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper, times(1)).updateById(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(cap.getValue().getErrorMsg()).contains("前置依赖检查失败");
        // 未进入计分
        verify(scoreMapper, never()).upsert(any());
        verify(schemeMapper, never()).selectByCondition(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void calculate_happyPathEmp_evaluatesFormulaAndUpserts() {
        when(taskMapper.selectCount(any())).thenReturn(1L); // 三级均已完成

        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        scheme.setStatus("ACTIVE");
        when(schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000))
                .thenReturn(List.of(scheme));

        PerfKpiItem item = new PerfKpiItem();
        item.setId("I1");
        item.setSchemeId("S1");
        item.setMetricCode("M_0001");
        item.setWeight(new BigDecimal("0.5"));
        item.setFormula("actual / target * weight");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(item));

        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("P1");
        plan.setKpiSchemeId("S1");
        plan.setTargetCycle("YEAR");
        when(targetPlanMapper.selectByCondition("S1", "ACTIVE", null, 0, 1000))
                .thenReturn(List.of(plan));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_0001");
        def.setBaseDim("EMP");
        def.setValSlot(5);
        when(metricDefService.getByCodeOrNull("M_0001")).thenReturn(def);

        when(empIndexResultMapper.selectLatestSlotValuesByDate(DATA_DATE, 5))
                .thenReturn(List.of(new SubjectSlotValueRow("E001", new BigDecimal("80"))));

        PerfTargetValue tv = new PerfTargetValue();
        tv.setTargetValue(new BigDecimal("100"));
        tv.setBaseValue(new BigDecimal("0"));
        when(targetValueMapper.selectByUniqueKey("P1", "EMP", "E001", "2026", "M_0001"))
                .thenReturn(tv);

        when(formulaService.evalScore(eq("actual / target * weight"),
                eq(new BigDecimal("80")), eq(new BigDecimal("100")),
                eq(new BigDecimal("0")), eq(new BigDecimal("0.5")), any(), any()))
                .thenReturn(new BigDecimal("0.4000"));

        String taskId = service.calculate(DATA_DATE, null, "MANUAL", "tester01");
        assertThat(taskId).isNotBlank();

        ArgumentCaptor<PerfKpiScore> scoreCap = ArgumentCaptor.forClass(PerfKpiScore.class);
        verify(scoreMapper, times(1)).upsert(scoreCap.capture());
        PerfKpiScore s = scoreCap.getValue();
        assertThat(s.getDataDate()).isEqualTo(DATA_DATE);
        assertThat(s.getSchemeCode()).isEqualTo("KPI_A");
        assertThat(s.getMetricCode()).isEqualTo("M_0001");
        assertThat(s.getSubjectType()).isEqualTo("EMP");
        assertThat(s.getSubjectId()).isEqualTo("E001");
        assertThat(s.getActualValue()).isEqualByComparingTo("80");
        assertThat(s.getTargetValue()).isEqualByComparingTo("100");
        assertThat(s.getBaseValue()).isEqualByComparingTo("0");
        assertThat(s.getWeight()).isEqualByComparingTo("0.5");
        assertThat(s.getScore()).isEqualByComparingTo("0.4");

        // 任务 SUCCESS
        ArgumentCaptor<PerfMetricCalcTask> taskCap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper, times(1)).updateById(taskCap.capture());
        assertThat(taskCap.getValue().getStatus()).isEqualTo("SUCCESS");

        // 方案级计算记录：一条 SUCCESS，含触发方式/触发人/方案编码
        ArgumentCaptor<com.bank.branch.platform.performance.entity.PerfKpiCalcLog> logCap =
                ArgumentCaptor.forClass(com.bank.branch.platform.performance.entity.PerfKpiCalcLog.class);
        verify(kpiCalcLogMapper, times(1)).insert(logCap.capture());
        com.bank.branch.platform.performance.entity.PerfKpiCalcLog calcLog = logCap.getValue();
        assertThat(calcLog.getSchemeCode()).isEqualTo("KPI_A");
        assertThat(calcLog.getResult()).isEqualTo("SUCCESS");
        assertThat(calcLog.getTriggerType()).isEqualTo("MANUAL");
        assertThat(calcLog.getTriggerBy()).isEqualTo("tester01");
        assertThat(calcLog.getDataDate()).isEqualTo(DATA_DATE);
    }

    @Test
    void calculate_sqlExpr_executesSqlAndUpserts() {
        when(taskMapper.selectCount(any())).thenReturn(1L); // 三级均已完成

        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        scheme.setStatus("ACTIVE");
        when(schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000))
                .thenReturn(List.of(scheme));

        PerfKpiItem item = new PerfKpiItem();
        item.setId("I1");
        item.setSchemeId("S1");
        item.setMetricCode("M_0001");
        item.setWeight(new BigDecimal("0.5"));
        item.setMaxScore(new BigDecimal("120"));
        item.setMinScore(new BigDecimal("0"));
        // 配置 SQL 表达式（优先于公式）；公式留空。结果只需 kpi_value 列，obj_id 由传入 objId 决定
        item.setSqlExpr("SELECT :actual / :target * :weight AS kpi_value");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(item));

        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("P1");
        plan.setKpiSchemeId("S1");
        plan.setTargetCycle("YEAR");
        when(targetPlanMapper.selectByCondition("S1", "ACTIVE", null, 0, 1000))
                .thenReturn(List.of(plan));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_0001");
        def.setBaseDim("EMP");
        def.setValSlot(5);
        when(metricDefService.getByCodeOrNull("M_0001")).thenReturn(def);

        when(empIndexResultMapper.selectLatestSlotValuesByDate(DATA_DATE, 5))
                .thenReturn(List.of(new SubjectSlotValueRow("E001", new BigDecimal("80"))));

        PerfTargetValue tv = new PerfTargetValue();
        tv.setTargetValue(new BigDecimal("100"));
        tv.setBaseValue(new BigDecimal("0"));
        when(targetValueMapper.selectByUniqueKey("P1", "EMP", "E001", "2026", "M_0001"))
                .thenReturn(tv);

        // SQL 执行器返回该对象的 KPI 得分（标量 kpi_value）
        when(sqlExecutor.executeScore(eq(item.getSqlExpr()), any(), any()))
                .thenReturn(new BigDecimal("0.4000"));

        String taskId = service.calculate(DATA_DATE, null, "MANUAL", "tester01");
        assertThat(taskId).isNotBlank();

        ArgumentCaptor<PerfKpiScore> scoreCap = ArgumentCaptor.forClass(PerfKpiScore.class);
        verify(scoreMapper, times(1)).upsert(scoreCap.capture());
        PerfKpiScore s = scoreCap.getValue();
        assertThat(s.getSubjectType()).isEqualTo("EMP");
        assertThat(s.getSubjectId()).isEqualTo("E001");
        assertThat(s.getActualValue()).isEqualByComparingTo("80");
        assertThat(s.getTargetValue()).isEqualByComparingTo("100");
        assertThat(s.getBaseValue()).isEqualByComparingTo("0");
        assertThat(s.getWeight()).isEqualByComparingTo("0.5");
        assertThat(s.getScore()).isEqualByComparingTo("0.4");

        // SQL 路径下不应再走公式引擎
        verify(formulaService, never()).evalScore(any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<PerfMetricCalcTask> taskCap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper, times(1)).updateById(taskCap.capture());
        assertThat(taskCap.getValue().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void calculate_blankFormula_skipsItemNotFail() {
        when(taskMapper.selectCount(any())).thenReturn(1L);

        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000))
                .thenReturn(List.of(scheme));

        PerfKpiItem item = new PerfKpiItem();
        item.setSchemeId("S1");
        item.setMetricCode("M_0001");
        item.setWeight(new BigDecimal("1"));
        item.setFormula(null); // 未配置公式
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(item));
        when(targetPlanMapper.selectByCondition("S1", "ACTIVE", null, 0, 1000)).thenReturn(List.of());

        PerfMetricDef def = new PerfMetricDef();
        def.setBaseDim("EMP");
        def.setValSlot(5);
        when(metricDefService.getByCodeOrNull("M_0001")).thenReturn(def);
        when(empIndexResultMapper.selectLatestSlotValuesByDate(DATA_DATE, 5))
                .thenReturn(List.of(new SubjectSlotValueRow("E001", new BigDecimal("80"))));

        service.calculate(DATA_DATE, null, "MANUAL", "tester01");

        verify(scoreMapper, never()).upsert(any());
        ArgumentCaptor<PerfMetricCalcTask> taskCap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper, times(1)).updateById(taskCap.capture());
        assertThat(taskCap.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(taskCap.getValue().getSkipCount()).isEqualTo(1);
    }

    @Test
    void deriveCycleKey_yearAndQuarter() {
        assertThat(service.deriveCycleKey("YEAR", LocalDate.of(2026, 6, 3))).isEqualTo("2026");
        assertThat(service.deriveCycleKey("QUARTER", LocalDate.of(2026, 6, 3))).isEqualTo("2026Q2");
        assertThat(service.deriveCycleKey("QUARTER", LocalDate.of(2026, 1, 31))).isEqualTo("2026Q1");
        assertThat(service.deriveCycleKey(null, LocalDate.of(2026, 12, 31))).isEqualTo("2026");
    }
}
