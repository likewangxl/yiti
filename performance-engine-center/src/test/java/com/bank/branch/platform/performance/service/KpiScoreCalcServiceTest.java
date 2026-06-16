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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("calculate(runLogId): 用 SYS_JOB_RUN_LOG.id 作为 PERF_METRIC_CALC_TASK.id")
    void calculate_withRunLogId_usesItAsTaskId() {
        when(taskMapper.selectCount(any())).thenReturn(0L); // 依赖未完成→任务置FAILED，仅校验插入 id
        assertThatThrownBy(() -> service.calculate(DATA_DATE, null, "AUTO", null, "KPIRUN1234567890ABCDEF1234567890"))
                .isInstanceOf(PerfException.class);
        ArgumentCaptor<PerfMetricCalcTask> cap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper).insert(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo("KPIRUN1234567890ABCDEF1234567890");
    }

    @Test
    @DisplayName("calculate(无 runLogId 4参): 回退生成 32 位 UUID")
    void calculate_fourArg_generatesUuid() {
        when(taskMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.calculate(DATA_DATE, null, "AUTO", null))
                .isInstanceOf(PerfException.class);
        ArgumentCaptor<PerfMetricCalcTask> cap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper).insert(cap.capture());
        assertThat(cap.getValue().getId()).hasSize(32);
    }

    @Test
    @DisplayName("calculate: 前置依赖未完成→任务置 FAILED 且不计分")
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
        def.setStatus("ACTIVE");
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
        // 计算结果落库前先删除该数据日期+方案(KPI_A)的旧计分明细，再 upsert（避免脏数据）
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(scoreMapper);
        order.verify(scoreMapper).deleteByDateAndScheme(DATA_DATE, "KPI_A");
        order.verify(scoreMapper).upsert(any(PerfKpiScore.class));
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
        def.setStatus("ACTIVE");
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
        def.setStatus("ACTIVE");
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

        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, 1, 20);

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
    @DisplayName("pageScoreGroups: 对象名称模糊关键字 → 翻译成工号集合(UserApi.pageUsers)，复用 SCOPE_FILTER 走 DB 分页（不再全取内存筛）")
    void pageScoreGroups_filtersBySubjectNameKeyword() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1"); scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
        PerfKpiItem i1 = new PerfKpiItem(); i1.setMetricCode("M_0001");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(i1));
        PerfMetricDef d1 = new PerfMetricDef(); d1.setMetricCode("M_0001"); d1.setMetricName("新增客户");
        when(metricDefService.getByCodeOrNull("M_0001")).thenReturn(d1);

        // 方案B：关键字"张" → UserApi.pageUsers 翻译出工号 E001（仅命中的对象）
        UserDTO u1 = new UserDTO(); u1.setUsername("E001"); u1.setDisplayName("张三");
        when(userApi.pageUsers(eq("张"), anyInt(), anyInt()))
                .thenReturn(com.bank.branch.platform.common.web.PageResult.of(1, 5000, 1L, List.of(u1)));
        // searchOrgs 未打桩 → 返回 null（服务内兜底为空），机构维度无命中

        // 翻译后用 effective filter（empIds=[E001]）走 DB 分页：count + 当前页
        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), any())).thenReturn(1L);
        KpiSubjectGroupRow g1 = new KpiSubjectGroupRow();
        g1.setSubjectId("E001"); g1.setSubjectType("EMP"); g1.setTotalScore(new BigDecimal("0.7"));
        when(scoreMapper.selectSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), any(), eq(0), eq(20)))
                .thenReturn(List.of(g1));
        when(scoreMapper.selectByDateSchemeSubjects(eq(DATA_DATE), eq("KPI_A"), anyList()))
                .thenReturn(List.of(scoreRow("E001", "M_0001", "80", "100", "0", "0.7")));
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(u1));

        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", null, "张", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getSubjectId()).isEqualTo("E001");
        assertThat(page.getRecords().get(0).getSubjectName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("pageScoreGroups: ORG 维度 → 批量 getOrgsByCodes 回填机构名称，对象ID 替换为部门编号")
    void pageScoreGroups_orgEnrichesViaBatch() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1"); scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
        PerfKpiItem i1 = new PerfKpiItem(); i1.setMetricCode("M_ORG");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(i1));
        PerfMetricDef dor = new PerfMetricDef();
        dor.setMetricCode("M_ORG"); dor.setMetricName("机构指标"); dor.setBaseDim("ORG");
        when(metricDefService.getByCodeOrNull("M_ORG")).thenReturn(dor);

        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq("ORG"), any())).thenReturn(1L);
        KpiSubjectGroupRow g = new KpiSubjectGroupRow();
        g.setSubjectId("ORGX"); g.setSubjectType("ORG"); g.setTotalScore(new BigDecimal("0.5"));
        when(scoreMapper.selectSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq("ORG"), any(), eq(0), eq(20)))
                .thenReturn(List.of(g));
        when(scoreMapper.selectByDateSchemeSubjects(eq(DATA_DATE), eq("KPI_A"), anyList()))
                .thenReturn(List.of(scoreRow("ORGX", "M_ORG", "80", "100", "0", "0.5")));
        com.bank.branch.platform.auth.api.dto.OrgDTO org = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        org.setOrgCode("ORGX"); org.setDeptNo("D100"); org.setOrgName("某支行");
        when(orgApi.getOrgsByCodes(anyList())).thenReturn(List.of(org));

        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", "ORG", null, 1, 20);

        assertThat(page.getRecords()).hasSize(1);
        KpiScoreGroupRowDTO row = page.getRecords().get(0);
        assertThat(row.getSubjectName()).isEqualTo("某支行");
        assertThat(row.getSubjectId()).isEqualTo("D100"); // 对象ID 替换为部门编号
        verify(orgApi).getOrgsByCodes(anyList());
        verify(orgApi, never()).getOrg(any());
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
        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", "EMP", null, 1, 20);
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
            // getEmpIdsByOrg 返回的是 USER_ID（如 U1/U2），需经 getUserByEmpIds 解析成工号(username=E001/E002)，
            // 因为 PERF_KPI_SCORE.subject_id(EMP) 存的是工号而非 USER_ID
            when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(List.of("U1", "U2"));
            UserDTO d1 = new UserDTO(); d1.setEmpId("U1"); d1.setUsername("E001");
            UserDTO d2 = new UserDTO(); d2.setEmpId("U2"); d2.setUsername("E002");
            when(userApi.getUserByEmpIds(List.of("U1", "U2"))).thenReturn(List.of(d1, d2));
            PerfKpiScheme scheme = new PerfKpiScheme();
            scheme.setId("S1"); scheme.setSchemeCode("KPI_A");
            when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
            when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of());
            ArgumentCaptor<KpiScopeFilter> cap = ArgumentCaptor.forClass(KpiScopeFilter.class);
            when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), cap.capture())).thenReturn(0L);

            service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, 1, 20);

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

    @Test
    @DisplayName("calculate: 单方案失败时记录错误并继续计算下一个方案（不中止整任务）")
    void calculate_oneSchemeFails_recordsErrorAndContinuesNext() {
        when(taskMapper.selectCount(any())).thenReturn(1L); // 三级均已完成

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1"); s1.setSchemeCode("KPI_A"); s1.setStatus("ACTIVE");
        PerfKpiScheme s2 = new PerfKpiScheme();
        s2.setId("S2"); s2.setSchemeCode("KPI_B"); s2.setStatus("ACTIVE");
        when(schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000))
                .thenReturn(List.of(s1, s2));

        // 方案1：calcOneScheme 取指标项时抛异常 → 方案计算失败
        when(itemMapper.selectBySchemeId("S1")).thenThrow(new RuntimeException("boom S1"));

        // 方案2：完整 happy-path（应在方案1失败后继续被处理）
        PerfKpiItem item2 = new PerfKpiItem();
        item2.setId("I2"); item2.setSchemeId("S2"); item2.setMetricCode("M_0002");
        item2.setWeight(new BigDecimal("0.5")); item2.setFormula("actual / target * weight");
        when(itemMapper.selectBySchemeId("S2")).thenReturn(List.of(item2));
        PerfTargetPlan plan2 = new PerfTargetPlan();
        plan2.setId("P2"); plan2.setKpiSchemeId("S2"); plan2.setTargetCycle("YEAR");
        when(targetPlanMapper.selectByCondition("S2", "ACTIVE", null, 0, 1000)).thenReturn(List.of(plan2));
        PerfMetricDef def2 = new PerfMetricDef();
        def2.setMetricCode("M_0002"); def2.setBaseDim("EMP"); def2.setValSlot(6); def2.setStatus("ACTIVE");
        when(metricDefService.getByCodeOrNull("M_0002")).thenReturn(def2);
        when(empIndexResultMapper.selectLatestSlotValuesByDate(DATA_DATE, 6))
                .thenReturn(List.of(new SubjectSlotValueRow("E001", new BigDecimal("80"))));
        PerfTargetValue tv2 = new PerfTargetValue();
        tv2.setTargetValue(new BigDecimal("100")); tv2.setBaseValue(new BigDecimal("0"));
        when(targetValueMapper.selectByUniqueKey("P2", "EMP", "E001", "2026", "M_0002")).thenReturn(tv2);
        when(formulaService.evalScore(eq("actual / target * weight"),
                eq(new BigDecimal("80")), eq(new BigDecimal("100")),
                eq(new BigDecimal("0")), eq(new BigDecimal("0.5")), any(), any()))
                .thenReturn(new BigDecimal("0.4000"));

        String taskId = service.calculate(DATA_DATE, null, "MANUAL", "tester01");

        // 未抛异常 → 任务完成；关键：方案1失败后仍继续处理了方案2（取指标项 + 完成计分）
        assertThat(taskId).isNotBlank();
        verify(itemMapper).selectBySchemeId("S2");
        verify(scoreMapper, times(1)).upsert(any(PerfKpiScore.class));
        // 任务 SUCCESS（部分失败，非全失败）+ 成功/失败方案计数
        ArgumentCaptor<PerfMetricCalcTask> taskCap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);
        verify(taskMapper, times(1)).updateById(taskCap.capture());
        assertThat(taskCap.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(taskCap.getValue().getSuccessCount()).isEqualTo(1);
        assertThat(taskCap.getValue().getFailCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("pageLogs: trigger_by 为 user_id(非工号) 时按 user_id 兜底回填触发人姓名")
    void pageLogs_triggerByUserId_resolvesNameViaEmpIdFallback() {
        com.bank.branch.platform.performance.entity.PerfKpiCalcLog logRow =
                new com.bank.branch.platform.performance.entity.PerfKpiCalcLog();
        logRow.setId(1L);
        logRow.setTriggerType("MANUAL");
        logRow.setTriggerBy("E40001");
        when(kpiCalcLogMapper.selectCount(any())).thenReturn(1L);
        when(kpiCalcLogMapper.selectList(any())).thenReturn(List.of(logRow));
        // 非工号(username)，按 username 查不到
        when(userApi.getUsersByUsernames(List.of("E40001"))).thenReturn(List.of());
        // 按 user_id 兜底命中
        UserDTO u = new UserDTO();
        u.setEmpId("E40001"); u.setUsername("finance_zhou"); u.setDisplayName("周八(资财)");
        when(userApi.getUserByEmpIds(List.of("E40001"))).thenReturn(List.of(u));

        var page = service.pageLogs(null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(1);
        // 展示值用真实工号(username)覆盖 user_id，并回填姓名 → 前端展示「工号 + 姓名」
        assertThat(page.getRecords().get(0).getTriggerBy()).isEqualTo("finance_zhou");
        assertThat(page.getRecords().get(0).getTriggerByName()).isEqualTo("周八(资财)");
    }

    // ==================== 指标不存在 / 状态非已发布 → 跳过该指标项 ====================

    @Test
    @DisplayName("calcOneScheme: KPI 指标不存在 → 跳过该项不抛异常，方案继续完成（不计分）")
    void calculate_metricNotFound_skipsItemWithoutThrow() {
        when(taskMapper.selectCount(any())).thenReturn(1L); // 1/2/3 级均已完成
        PerfKpiScheme s = new PerfKpiScheme();
        s.setId("S1"); s.setSchemeCode("KPI_A"); s.setStatus("ACTIVE");
        when(schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000)).thenReturn(List.of(s));
        PerfKpiItem item = new PerfKpiItem();
        item.setId("I1"); item.setSchemeId("S1"); item.setMetricCode("M_MISSING");
        item.setWeight(new BigDecimal("0.5")); item.setFormula("actual / target * weight");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(item));
        // 指标不存在
        when(metricDefService.getByCodeOrNull("M_MISSING")).thenReturn(null);

        String taskId = service.calculate(DATA_DATE, null, "MANUAL", "tester01");

        assertThat(taskId).isNotBlank();
        // 该指标被跳过：不计分落库，且不抛异常中断
        verify(scoreMapper, never()).upsert(any(PerfKpiScore.class));
    }

    @Test
    @DisplayName("calcOneScheme: KPI 指标状态非已发布(DRAFT) → 跳过该项，方案继续完成（不计分）")
    void calculate_metricNotPublished_skipsItem() {
        when(taskMapper.selectCount(any())).thenReturn(1L);
        PerfKpiScheme s = new PerfKpiScheme();
        s.setId("S1"); s.setSchemeCode("KPI_A"); s.setStatus("ACTIVE");
        when(schemeMapper.selectByCondition(null, "ACTIVE", null, null, 0, 100000)).thenReturn(List.of(s));
        PerfKpiItem item = new PerfKpiItem();
        item.setId("I1"); item.setSchemeId("S1"); item.setMetricCode("M_DRAFT");
        item.setWeight(new BigDecimal("0.5")); item.setFormula("actual / target * weight");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(item));
        PerfMetricDef draftDef = new PerfMetricDef();
        draftDef.setMetricCode("M_DRAFT"); draftDef.setBaseDim("EMP"); draftDef.setValSlot(5);
        draftDef.setStatus("DRAFT"); // 草稿态 → 跳过
        when(metricDefService.getByCodeOrNull("M_DRAFT")).thenReturn(draftDef);

        String taskId = service.calculate(DATA_DATE, null, "MANUAL", "tester01");

        assertThat(taskId).isNotBlank();
        verify(scoreMapper, never()).upsert(any(PerfKpiScore.class));
    }
}
