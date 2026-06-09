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
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.mapper.SubjectSlotValueRow;
import com.bank.branch.platform.performance.mapper.KpiSubjectGroupRow;
import com.bank.branch.platform.performance.mapper.KpiScopeFilter;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupPageDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupRowDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreMetricCellDTO;
import com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    void getLatestLogDataDate_returnsMapperMaxDate() {
        when(kpiCalcLogMapper.selectMaxDataDate()).thenReturn(LocalDate.of(2026, 6, 8));
        assertThat(service.getLatestLogDataDate()).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    @Test
    void pageScoreGroups_groupsByObjectWithMetricCellsAndCompleteRate() {
        // 指标列（listSchemeMetrics 路径）
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
        PerfKpiItem i1 = new PerfKpiItem(); i1.setMetricCode("M_0001");
        PerfKpiItem i2 = new PerfKpiItem(); i2.setMetricCode("M_0002");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(i1, i2));
        PerfMetricDef d1 = new PerfMetricDef(); d1.setMetricCode("M_0001"); d1.setMetricName("新增客户");
        PerfMetricDef d2 = new PerfMetricDef(); d2.setMetricCode("M_0002"); d2.setMetricName("存款日均");
        when(metricDefService.getByCodeOrNull("M_0001")).thenReturn(d1);
        when(metricDefService.getByCodeOrNull("M_0002")).thenReturn(d2);

        // 分组：1 个对象 E001，考核得分合计 0.7
        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), any())).thenReturn(1L);
        KpiSubjectGroupRow g = new KpiSubjectGroupRow();
        g.setSubjectId("E001"); g.setSubjectType("EMP"); g.setTotalScore(new BigDecimal("0.7"));
        when(scoreMapper.selectSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), any(), eq(0), eq(20)))
                .thenReturn(List.of(g));

        // 该对象两条指标计分行
        when(scoreMapper.selectByDateSchemeSubjects(eq(DATA_DATE), eq("KPI_A"), anyList()))
                .thenReturn(List.of(
                        scoreRow("E001", "M_0001", "80", "100", "0", "0.4"),
                        scoreRow("E001", "M_0002", "50", "100", "10", "0.3")));

        UserDTO u = new UserDTO();
        u.setUsername("E001"); u.setDisplayName("张三");
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(u));

        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getMetrics()).extracting("metricName")
                .containsExactlyInAnyOrder("新增客户", "存款日均");
        assertThat(page.getRecords()).hasSize(1);
        KpiScoreGroupRowDTO row = page.getRecords().get(0);
        assertThat(row.getSubjectId()).isEqualTo("E001");
        assertThat(row.getSubjectName()).isEqualTo("张三");
        assertThat(row.getTotalScore()).isEqualByComparingTo("0.7");
        KpiScoreMetricCellDTO c1 = row.getMetrics().get("M_0001");
        assertThat(c1.getActual()).isEqualByComparingTo("80");
        assertThat(c1.getScore()).isEqualByComparingTo("0.4");
        // 完成率 = (80-0)/100*100 = 80.00
        assertThat(c1.getCompleteRate()).isEqualByComparingTo("80.00");
        KpiScoreMetricCellDTO c2 = row.getMetrics().get("M_0002");
        // 完成率 = (50-10)/100*100 = 40.00
        assertThat(c2.getCompleteRate()).isEqualByComparingTo("40.00");
    }

    @Test
    void pageScoreGroups_filtersMetricColumnsBySelectedDimension() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
        PerfKpiItem i1 = new PerfKpiItem(); i1.setMetricCode("M_EMP");
        PerfKpiItem i2 = new PerfKpiItem(); i2.setMetricCode("M_ORG");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(i1, i2));
        PerfMetricDef de = new PerfMetricDef(); de.setMetricCode("M_EMP"); de.setMetricName("员工指标"); de.setBaseDim("EMP");
        PerfMetricDef dor = new PerfMetricDef(); dor.setMetricCode("M_ORG"); dor.setMetricName("机构指标"); dor.setBaseDim("ORG");
        when(metricDefService.getByCodeOrNull("M_EMP")).thenReturn(de);
        when(metricDefService.getByCodeOrNull("M_ORG")).thenReturn(dor);

        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq("EMP"), any())).thenReturn(1L);
        KpiSubjectGroupRow g = new KpiSubjectGroupRow();
        g.setSubjectId("E001"); g.setSubjectType("EMP"); g.setTotalScore(new BigDecimal("0.4"));
        when(scoreMapper.selectSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq("EMP"), any(), eq(0), eq(20)))
                .thenReturn(List.of(g));
        when(scoreMapper.selectByDateSchemeSubjects(eq(DATA_DATE), eq("KPI_A"), anyList()))
                .thenReturn(List.of(scoreRow("E001", "M_EMP", "80", "100", "0", "0.4")));

        // 选中 EMP 维度 → 指标列只剩 EMP 维度指标，过滤掉 ORG 指标组
        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", "EMP", 1, 20);
        assertThat(page.getMetrics()).extracting("metricCode").containsExactly("M_EMP");
    }

    @Test
    void listScoresForExport_returnsEnrichedDetailRows() {
        PerfKpiScore s = scoreRow("E001", "M_0001", "80", "100", "0", "0.4");
        s.setDataDate(DATA_DATE); s.setSchemeCode("KPI_A"); s.setWeight(new BigDecimal("0.5"));
        when(scoreMapper.selectList(any())).thenReturn(List.of(s));
        PerfMetricDef def = new PerfMetricDef(); def.setMetricCode("M_0001"); def.setMetricName("新增客户");
        when(metricDefService.getByCodeOrNull("M_0001")).thenReturn(def);
        UserDTO u = new UserDTO(); u.setUsername("E001"); u.setDisplayName("张三");
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(u));

        List<PerfKpiScoreResultDTO> rows = service.listScoresForExport(DATA_DATE, "KPI_A", "EMP", 50000);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getMetricName()).isEqualTo("新增客户");
        assertThat(rows.get(0).getSubjectName()).isEqualTo("张三");
        assertThat(rows.get(0).getScore()).isEqualByComparingTo("0.4");
    }

    @Test
    void pageScoreGroups_appliesOrgSubtreeScope_orgCodesAndSubtreeEmpIds() {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setBizType(BizType.KPI_CALC);
        ctx.setScope(DataScopeType.ORG_SUBTREE);
        ctx.setEmpId("mgr");
        ctx.setOrgSubtreeCodes(new java.util.LinkedHashSet<>(List.of("ORG1")));
        DataScopeContext.set(ctx);
        try {
            when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(List.of("E001", "E002"));
            PerfKpiScheme scheme = new PerfKpiScheme();
            scheme.setId("S1"); scheme.setSchemeCode("KPI_A");
            when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
            when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of());
            ArgumentCaptor<KpiScopeFilter> cap = ArgumentCaptor.forClass(KpiScopeFilter.class);
            when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), cap.capture())).thenReturn(0L);

            service.pageScoreGroups(DATA_DATE, "KPI_A", null, 1, 20);

            KpiScopeFilter scope = cap.getValue();
            assertThat(scope.isScopeAll()).isFalse();
            assertThat(scope.getOrgCodes()).containsExactly("ORG1");
            assertThat(scope.getEmpIds()).containsExactlyInAnyOrder("E001", "E002");
        } finally {
            DataScopeContext.clear();
        }
    }

    private static PerfKpiScore scoreRow(String subjectId, String metricCode,
                                         String actual, String target, String base, String score) {
        PerfKpiScore s = new PerfKpiScore();
        s.setSubjectType("EMP");
        s.setSubjectId(subjectId);
        s.setMetricCode(metricCode);
        s.setActualValue(new BigDecimal(actual));
        s.setTargetValue(new BigDecimal(target));
        s.setBaseValue(new BigDecimal(base));
        s.setScore(new BigDecimal(score));
        return s;
    }
}
