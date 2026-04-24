package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricApiImpl.getUserMetricCards V1.3 R2.5 行为断言.
 *
 * <p>V1.3 简化策略：仅实现 target + actual + achievementRate，mom / yoy 留 V1.4。
 *
 * <p>组装流程：
 * <ol>
 *   <li>查 sys_control(EMP) → latest_data_date + current_version</li>
 *   <li>取所有 ACTIVE KPI 方案 → items → distinct metricCode（仅 EMP 维度）</li>
 *   <li>对每 metric：target 查 perf_target_value，actual 查 emp_index_result，
 *       achievementRate = actual / target * 100（target 为 0/null 时留 null）</li>
 *   <li>返回 List&lt;MetricCardDTO&gt;，按 KPI 方案 item 插入顺序排序</li>
 * </ol>
 */
class MetricApiImplCardsTest extends PerformanceServiceTestBase {

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private SysControlService sysControlService;

    @Mock
    private EmpIndexResultMapper empIndexResultMapper;

    @Mock
    private OrgIndexResultMapper orgIndexResultMapper;

    @Mock
    private CustIndexResultMapper custIndexResultMapper;

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private PerfTargetValueMapper perfTargetValueMapper;

    @InjectMocks
    private MetricApiImpl api;

    @Test
    @DisplayName("getUserMetricCards: 按 ACTIVE 方案 items 组装卡片, 携带 target / actual / achievementRate")
    void getUserMetricCards_returnsCardsWithTargetAndActual() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 4, 20);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setSchemeCode("SCHEME_A");
        s1.setCycleType("MONTHLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));

        PerfKpiItem it1 = kpiItem("S1", "M_EMP_A");
        PerfKpiItem it2 = kpiItem("S1", "M_EMP_B");
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(it1, it2));

        // metric def 信息（EMP 维度）
        PerfMetricDef defA = def("M_EMP_A", "EMP", 5, "指标一", "万元");
        PerfMetricDef defB = def("M_EMP_B", "EMP", 6, "指标二", "户");
        when(metricDefService.getByCodes(List.of("M_EMP_A", "M_EMP_B")))
                .thenReturn(List.of(defA, defB));

        // V1.5 P4.1: actual 走 batch API 一次拿回 current/previous/yearAgo 的 slot 值.
        // 本 case 只关注 currentValue + targetValue, previousValue/yearAgo 用 null 不构造
        // (Map 未命中 key 返 null, 等价 V1.4 单点 null 的表现).
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(latest, new BigDecimal("80")));
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(6)))
                .thenReturn(Map.of(latest, new BigDecimal("50")));

        // target
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), anyString(), eq("M_EMP_A")))
                .thenReturn(targetValue(new BigDecimal("100")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), anyString(), eq("M_EMP_B")))
                .thenReturn(targetValue(new BigDecimal("25")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(2);
        MetricCardDTO cA = cards.stream()
                .filter(c -> "M_EMP_A".equals(c.getMetricCode())).findFirst().orElseThrow();
        assertThat(cA.getMetricName()).isEqualTo("指标一");
        assertThat(cA.getUnit()).isEqualTo("万元");
        assertThat(cA.getCurrentValue()).isEqualByComparingTo("80");
        assertThat(cA.getTargetValue()).isEqualByComparingTo("100");
        assertThat(cA.getAchievementRate()).isEqualByComparingTo("80"); // 80/100*100
        assertThat(cA.getDataDate()).isEqualTo(latest);

        MetricCardDTO cB = cards.stream()
                .filter(c -> "M_EMP_B".equals(c.getMetricCode())).findFirst().orElseThrow();
        assertThat(cB.getTargetValue()).isEqualByComparingTo("25");
        assertThat(cB.getCurrentValue()).isEqualByComparingTo("50");
        assertThat(cB.getAchievementRate()).isEqualByComparingTo("200"); // 50/25*100
    }

    @Test
    @DisplayName("getUserMetricCards: target=null 时 achievementRate 留 null 但卡片保留")
    void getUserMetricCards_nullTarget_achievementRateNull() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 4, 20);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setSchemeCode("S_A");
        s1.setCycleType("MONTHLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_EMP_A")));
        when(metricDefService.getByCodes(List.of("M_EMP_A")))
                .thenReturn(List.of(def("M_EMP_A", "EMP", 5, "A", "万元")));
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(latest, new BigDecimal("10")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), anyString(), eq("M_EMP_A")))
                .thenReturn(null);

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getCurrentValue()).isEqualByComparingTo("10");
        assertThat(cards.get(0).getTargetValue()).isNull();
        assertThat(cards.get(0).getAchievementRate()).isNull();
    }

    @Test
    @DisplayName("getUserMetricCards: target=0 时 achievementRate 留 null (防除零)")
    void getUserMetricCards_zeroTarget_achievementRateNull() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 4, 20);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setSchemeCode("S_A");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_EMP_A")));
        when(metricDefService.getByCodes(List.of("M_EMP_A")))
                .thenReturn(List.of(def("M_EMP_A", "EMP", 5, "A", "万元")));
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(latest, new BigDecimal("10")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), anyString(), eq("M_EMP_A")))
                .thenReturn(targetValue(BigDecimal.ZERO));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getTargetValue()).isEqualByComparingTo("0");
        assertThat(cards.get(0).getAchievementRate()).isNull();
    }

    @Test
    @DisplayName("getUserMetricCards: 无 ACTIVE 方案时返回空列表 (不抛异常)")
    void getUserMetricCards_noActiveScheme_returnsEmpty() {
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of());

        List<MetricCardDTO> cards = api.getUserMetricCards("E001");

        assertThat(cards).isEmpty();
    }

    @Test
    @DisplayName("getUserMetricCards: 非 EMP 维度 metric 被过滤, 不出现在卡片列表中")
    void getUserMetricCards_nonEmpMetricSkipped() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 4, 20);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(
                kpiItem("S1", "M_EMP_OK"),
                kpiItem("S1", "M_ORG_SKIP")));
        when(metricDefService.getByCodes(List.of("M_EMP_OK", "M_ORG_SKIP")))
                .thenReturn(List.of(
                        def("M_EMP_OK", "EMP", 1, "EMP ok", "万元"),
                        def("M_ORG_SKIP", "ORG", 2, "ORG skip", "户")));
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(1)))
                .thenReturn(Map.of(latest, new BigDecimal("5")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), anyString(), eq("M_EMP_OK")))
                .thenReturn(targetValue(new BigDecimal("10")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getMetricCode()).isEqualTo("M_EMP_OK");
    }

    // ============ V1.4 S3.1 扩字段存在断言 ============

    @Test
    @DisplayName("[V1.4 S3.1] MetricCardDTO 包含 previousValue/mom/yoy 3 个字段且可 JSON 序列化")
    void metricCardDTO_hasExtendedFields() throws Exception {
        MetricCardDTO dto = MetricCardDTO.builder()
                .metricCode("M_X")
                .currentValue(new BigDecimal("120"))
                .targetValue(new BigDecimal("100"))
                .previousValue(new BigDecimal("100"))
                .mom(new BigDecimal("20.00"))
                .yoy(new BigDecimal("50.00"))
                .build();
        String json = new ObjectMapper().writeValueAsString(dto);
        assertThat(json).contains("\"previousValue\"").contains("\"mom\"").contains("\"yoy\"");
    }

    @Test
    @DisplayName("[V1.4 S3.1] previousValue/mom/yoy 允许 null（兼容 V1.3 简化策略）")
    void metricCardDTO_extendedFields_allowsNull() {
        MetricCardDTO dto = MetricCardDTO.builder()
                .metricCode("M_X")
                .currentValue(new BigDecimal("100"))
                .targetValue(new BigDecimal("100"))
                .build();
        assertThat(dto.getPreviousValue()).isNull();
        assertThat(dto.getMom()).isNull();
        assertThat(dto.getYoy()).isNull();
    }

    // ============ V1.4 S3.2 cycleKey 按 cycleType 精确匹配 ============

    /**
     * 给定 cycleType + latestDataDate, 断言查 TargetValue 时传的 cycleKey 与期望一致.
     *
     * <p>现状签名 getUserMetricCards(empId) 单参, cycleType 从 scheme.cycleType 读,
     * latestDataDate 从 sys_control 读. 本组 case 用 Mock 替换依赖, 捕获
     * perfTargetValueMapper.selectByUniqueKey 的 cycleKey 入参.
     */
    private void runCycleKeyCase(String cycleType, LocalDate latest, String expectedCycleKey) {
        String empId = "E001";
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setSchemeCode("S_X");
        s1.setCycleType(cycleType);
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(latest, new BigDecimal("10")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq(expectedCycleKey), eq("M_X")))
                .thenReturn(targetValue(new BigDecimal("20")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        // 严格验证 cycleKey 形态: 命中 targetValue 说明 expectedCycleKey 被调用;
        // 若未命中则 targetValue=null → achievementRate=null, 本断言失败
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getTargetValue())
                .as("cycleType=%s latest=%s 期望 cycleKey=%s", cycleType, latest, expectedCycleKey)
                .isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("[V1.4 S3.2] YEARLY → cycleKey = yyyy")
    void getUserMetricCards_yearlyCycleType_buildsYearKey() {
        runCycleKeyCase("YEARLY", LocalDate.of(2026, 7, 15), "2026");
    }

    @Test
    @DisplayName("[V1.4 S3.2] QUARTERLY → cycleKey = yyyyQn (Q2)")
    void getUserMetricCards_quarterlyCycleType_buildsQuarterKey() {
        runCycleKeyCase("QUARTERLY", LocalDate.of(2026, 5, 15), "2026Q2");
    }

    @Test
    @DisplayName("[V1.4 S3.2] MONTHLY → cycleKey = yyyyMM")
    void getUserMetricCards_monthlyCycleType_buildsMonthKey() {
        runCycleKeyCase("MONTHLY", LocalDate.of(2026, 7, 15), "202607");
    }

    @Test
    @DisplayName("[V1.4 S3.2] WEEKLY → cycleKey = yyyyWww (ISO)")
    void getUserMetricCards_weeklyCycleType_buildsWeekKey() {
        // 2026-01-15 为 ISO 周 year=2026, week=03
        runCycleKeyCase("WEEKLY", LocalDate.of(2026, 1, 15), "2026W03");
    }

    // ============ V1.4 S3.3 mom 环比计算 ============

    @Test
    @DisplayName("[V1.4 S3.3] mom: previous 非 0 时 = (current-previous)/|previous|*100, 2 位小数")
    void getUserMetricCards_calculatesMom_whenPreviousValueAvailable() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 4, 1);  // QUARTERLY: Q2 起始
        LocalDate previous = LocalDate.of(2026, 1, 1); // QUARTERLY 上一季起始 (current.minusMonths(3))

        SysControl sc = new SysControl();
        sc.setCurrentVersion("v20260401");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // V1.5 P4.1: batch API 一次拿回 current=120 / previous=100, yearAgo 未 stub → null
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v20260401"), eq(5)))
                .thenReturn(Map.of(current, new BigDecimal("120"), previous, new BigDecimal("100")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getPreviousValue()).isEqualByComparingTo("100");
        assertThat(cards.get(0).getMom()).isEqualByComparingTo("20.00"); // (120-100)/100*100
    }

    @Test
    @DisplayName("[V1.4 S3.3] mom: previous=0 时返回 null 防除零")
    void getUserMetricCards_momZeroWhenPreviousIsZero_returnsNullSafely() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 4, 1);
        LocalDate previous = LocalDate.of(2026, 1, 1);

        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // V1.5 P4.1: batch API 一次返回 current=120 / previous=ZERO
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(current, new BigDecimal("120"), previous, BigDecimal.ZERO));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getMom()).isNull();
    }

    @Test
    @DisplayName("[V1.4 S3.3] mom: previous 未命中时 previousValue + mom 同步为 null")
    void getUserMetricCards_momNullWhenPreviousNotFound() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 4, 1);
        LocalDate previous = LocalDate.of(2026, 1, 1);

        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // V1.5 P4.1: batch 返回仅含 current（previous 未命中 → Map 不含 key = null）
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(current, new BigDecimal("120")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getPreviousValue()).isNull();
        assertThat(cards.get(0).getMom()).isNull();
    }

    // ============ V1.4 S3.4 yoy 同比计算 ============

    @Test
    @DisplayName("[V1.4 S3.4] yoy: yearAgo 非 0 时 = (current-yearAgo)/|yearAgo|*100, 2 位小数")
    void getUserMetricCards_calculatesYoy_whenYearAgoAvailable() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 4, 1);
        LocalDate previous = LocalDate.of(2026, 1, 1); // QUARTERLY 上一季
        LocalDate yearAgo = LocalDate.of(2025, 4, 1);  // current.minusYears(1)

        SysControl sc = new SysControl();
        sc.setCurrentVersion("v");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // V1.5 P4.1: batch 返回 current=150 / yearAgo=100; previous 不入 Map → null（聚焦 yoy）
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v"), eq(5)))
                .thenReturn(Map.of(current, new BigDecimal("150"), yearAgo, new BigDecimal("100")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getYoy()).isEqualByComparingTo("50.00"); // (150-100)/100*100
    }

    @Test
    @DisplayName("[V1.4 S3.4] yoy: yearAgo 未命中时返回 null")
    void getUserMetricCards_yoyNullWhenYearAgoNotFound() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 4, 1);
        LocalDate previous = LocalDate.of(2026, 1, 1);
        LocalDate yearAgo = LocalDate.of(2025, 4, 1);

        SysControl sc = new SysControl();
        sc.setCurrentVersion("v");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // V1.5 P4.1: batch 仅返回 current, previous / yearAgo 不入 Map → null
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v"), eq(5)))
                .thenReturn(Map.of(current, new BigDecimal("150")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getYoy()).isNull();
    }

    // ============ V1.5 P3.1 多 scheme 共享 metric 按 (metricCode, cycleType) 组合分组 ============

    /**
     * V1.5 P3.1 Red：同一 metricCode 被多个 scheme 引用且 cycleType 不同时，
     * 生成按 (metricCode, cycleType) 分组的多张卡片.
     *
     * <p>V1.4 reviewer M01 观察项：当前首命中策略会让 YEARLY scheme 的目标值永远查不到.
     * V1.5 修复：按 cycleType 分组 × metricCode，每个组合一张卡片.
     */
    @Test
    @DisplayName("[V1.5 P3.1] 多 scheme 共享 metric 不同 cycleType：按 (metric, cycleType) 分组生成多卡片")
    void getUserMetricCards_multiSchemesDifferentCycleType_generatesCardPerCombo() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 7, 15);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        // scheme A (QUARTERLY) + scheme B (YEARLY) 都引用 M_X
        PerfKpiScheme sA = new PerfKpiScheme();
        sA.setId("SA");
        sA.setCycleType("QUARTERLY");
        sA.setStatus("ACTIVE");
        PerfKpiScheme sB = new PerfKpiScheme();
        sB.setId("SB");
        sB.setCycleType("YEARLY");
        sB.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(sA, sB));
        when(kpiItemService.listBySchemeId("SA")).thenReturn(List.of(kpiItem("SA", "M_X")));
        when(kpiItemService.listBySchemeId("SB")).thenReturn(List.of(kpiItem("SB", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // V1.5 P4.1: batch API 每卡片 1 次, 用 latest 做 current 即可
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(5)))
                .thenReturn(Map.of(latest, new BigDecimal("80")));

        // QUARTERLY 方向下 cycleKey=2026Q3；YEARLY 方向下 cycleKey=2026
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026Q3"), eq("M_X")))
                .thenReturn(targetValue(new BigDecimal("100")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026"), eq("M_X")))
                .thenReturn(targetValue(new BigDecimal("400")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        // V1.5：同一 metricCode 分 2 张卡片，分别命中不同 cycleType 的 target
        assertThat(cards).hasSize(2);
        assertThat(cards).extracting(MetricCardDTO::getTargetValue)
                .extracting(bd -> bd == null ? null : bd.stripTrailingZeros().toString())
                .containsExactlyInAnyOrder("1E+2", "4E+2"); // 100 和 400 经 strip 后为 1E+2 / 4E+2

        // 验证两张卡片分别对两个 cycleType 的 target 做了精确查询
        verify(perfTargetValueMapper).selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026Q3"), eq("M_X"));
        verify(perfTargetValueMapper).selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026"), eq("M_X"));
    }

    /**
     * V1.5 P3.1 Red：同一 metricCode 多个 scheme cycleType 相同时，仍只出一张卡片（去重）.
     *
     * <p>防止 P3 过度修复变成"每 scheme 一卡"，保持"按 cycleType 去重"语义.
     */
    @Test
    @DisplayName("[V1.5 P3.1] 多 scheme 同 cycleType 引用同 metric：去重后仅一张卡片")
    void getUserMetricCards_multiSchemesSameCycleType_generatesSingleCard() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 7, 15);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme sA = new PerfKpiScheme();
        sA.setId("SA");
        sA.setCycleType("MONTHLY");
        sA.setStatus("ACTIVE");
        PerfKpiScheme sB = new PerfKpiScheme();
        sB.setId("SB");
        sB.setCycleType("MONTHLY");
        sB.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(sA, sB));
        when(kpiItemService.listBySchemeId("SA")).thenReturn(List.of(kpiItem("SA", "M_Y")));
        when(kpiItemService.listBySchemeId("SB")).thenReturn(List.of(kpiItem("SB", "M_Y")));
        when(metricDefService.getByCodes(List.of("M_Y")))
                .thenReturn(List.of(def("M_Y", "EMP", 6, "Y", "户")));
        // V1.5 P4.1: batch API, 两 scheme 共 1 卡片（去重后），1 次 batch 调用
        lenient().when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(6)))
                .thenReturn(Map.of(latest, new BigDecimal("5")));
        when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("202607"), eq("M_Y")))
                .thenReturn(targetValue(new BigDecimal("10")));

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getMetricCode()).isEqualTo("M_Y");
        assertThat(cards.get(0).getTargetValue()).isEqualByComparingTo("10");
    }

    // ============ V1.5 P4.1 buildCard 宽表查询 batch 合并 ============

    /**
     * V1.5 P4.1 Red：buildCard 必须改为调用 selectSlotValuesByDates batch API，
     * 而不是 3 次单点 selectSlotValue.
     */
    @Test
    @DisplayName("[V1.5 P4.1] buildCard 改用 batch 查询：每卡片只调一次 selectSlotValuesByDates")
    void getUserMetricCards_batchesWideTableQueries() {
        String empId = "E001";
        LocalDate latest = LocalDate.of(2026, 4, 1);
        LocalDate previous = LocalDate.of(2026, 1, 1);
        LocalDate yearAgo = LocalDate.of(2025, 4, 1);
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(latest);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
        when(metricDefService.getByCodes(List.of("M_X")))
                .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

        // 期望 Facade 调 batch API 一次拿 3 个日期
        Map<LocalDate, BigDecimal> batchMap = new HashMap<>();
        batchMap.put(latest, new BigDecimal("120"));
        batchMap.put(previous, new BigDecimal("100"));
        batchMap.put(yearAgo, new BigDecimal("80"));
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), argThat(list -> list != null && list.containsAll(List.of(latest, previous, yearAgo))),
                eq("v1"), eq(5)))
                .thenReturn(batchMap);

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getCurrentValue()).isEqualByComparingTo("120");
        assertThat(cards.get(0).getPreviousValue()).isEqualByComparingTo("100");
        // yoy = (120-80)/80 * 100 = 50.00
        assertThat(cards.get(0).getYoy()).isEqualByComparingTo("50.00");

        // 关键断言：batch 方法调用次数 = 卡片数 = 1（而非 3 次单点）
        verify(empIndexResultMapper, times(1))
                .selectSlotValuesByDates(anyString(), anyList(), anyString(), anyInt());
        // 旧单点方法不应再被调用
        verify(empIndexResultMapper, never())
                .selectSlotValue(anyString(), any(LocalDate.class), anyString(), anyInt());
    }

    // ============ V1.5 P5.1 WEEKLY yoy 走 minusWeeks(52) 分支 ============

    /**
     * V1.5 P5.1 Red：WEEKLY cycleType 的 yoy 必须走 -52 周，而非 -1 年.
     *
     * <p>V1.4 reviewer M03 观察：统一 minusYears(1) 在 WEEKLY 下跨 ISO 年边界时
     * 周次偏移。V1.5 分支处理：WEEKLY → minusWeeks(52)，其他不变.
     */
    @Test
    @DisplayName("[V1.5 P5.1] WEEKLY yoy：yearAgoDate 走 minusWeeks(52)，不同于 minusYears(1)")
    void getUserMetricCards_weeklyYoy_usesMinusWeeks52() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 12, 28); // ISO 2026W53
        LocalDate previousWeek = current.minusWeeks(1); // WEEKLY 前一周 = 2026-12-21
        LocalDate yearAgoWeek52 = current.minus(52, java.time.temporal.ChronoUnit.WEEKS); // 2026-01-05 = ISO 2026W01
        LocalDate minusYearsFallback = current.minusYears(1); // 2025-12-28 = ISO 2025W52 (若走旧逻辑)

        // Red 设计：仅 stub minus-52-week 路径，旧 minusYears 路径未 stub，走旧逻辑会拿 null
        SysControl sc = new SysControl();
        sc.setCurrentVersion("v1");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);
        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("WEEKLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_W")));
        when(metricDefService.getByCodes(List.of("M_W")))
                .thenReturn(List.of(def("M_W", "EMP", 7, "W", "户")));

        // V1.5 P4.1 后用 batch Map；Mock 按"-52 周"路径返回值
        Map<LocalDate, BigDecimal> batch = new HashMap<>();
        batch.put(current, new BigDecimal("200"));
        batch.put(previousWeek, new BigDecimal("180"));
        batch.put(yearAgoWeek52, new BigDecimal("150")); // 关键：只在 -52 周命中
        // 故意不 put minusYearsFallback，走旧逻辑会 yearAgoValue=null → yoy=null
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v1"), eq(7)))
                .thenReturn(batch);

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        // 走 -52 周路径：yoy = (200-150)/150*100 = 33.33
        assertThat(cards.get(0).getYoy())
                .as("WEEKLY cycleType 应走 minusWeeks(52)，命中 150 的 yearAgo 值")
                .isEqualByComparingTo("33.33");
    }

    /**
     * V1.5 P5.1 Red：非 WEEKLY cycleType 的 yoy 行为保持不变（仍 minusYears(1)）.
     */
    @Test
    @DisplayName("[V1.5 P5.1] YEARLY/QUARTERLY/MONTHLY yoy 保持 minusYears(1) 不变")
    void getUserMetricCards_nonWeeklyYoy_unchanged_usesMinusYears1() {
        String empId = "E001";
        LocalDate current = LocalDate.of(2026, 4, 1);
        LocalDate previous = LocalDate.of(2026, 1, 1);       // QUARTERLY 上一季
        LocalDate yearAgo = LocalDate.of(2025, 4, 1);         // minusYears(1)
        LocalDate wrongMinus52Weeks = current.minus(52, java.time.temporal.ChronoUnit.WEEKS); // 2025-04-02（与 yearAgo 差 1 天）

        SysControl sc = new SysControl();
        sc.setCurrentVersion("v");
        sc.setLatestDataDate(current);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);
        PerfKpiScheme s1 = new PerfKpiScheme();
        s1.setId("S1");
        s1.setCycleType("QUARTERLY");
        s1.setStatus("ACTIVE");
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
        when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_Q")));
        when(metricDefService.getByCodes(List.of("M_Q")))
                .thenReturn(List.of(def("M_Q", "EMP", 8, "Q", "万元")));

        // 只 stub minusYears(1) 路径；-52 周路径故意不填值
        Map<LocalDate, BigDecimal> batch = new HashMap<>();
        batch.put(current, new BigDecimal("150"));
        batch.put(previous, new BigDecimal("130"));
        batch.put(yearAgo, new BigDecimal("100"));
        // 故意不 put wrongMinus52Weeks
        when(empIndexResultMapper.selectSlotValuesByDates(
                eq(empId), anyList(), eq("v"), eq(8)))
                .thenReturn(batch);

        List<MetricCardDTO> cards = api.getUserMetricCards(empId);

        assertThat(cards).hasSize(1);
        // QUARTERLY 仍走 minusYears(1)：yoy = (150-100)/100*100 = 50.00
        assertThat(cards.get(0).getYoy()).isEqualByComparingTo("50.00");
    }

    // ============ helpers ============

    private static PerfKpiItem kpiItem(String schemeId, String metricCode) {
        PerfKpiItem it = new PerfKpiItem();
        it.setSchemeId(schemeId);
        it.setMetricCode(metricCode);
        it.setWeight(new BigDecimal("50"));
        return it;
    }

    private static PerfMetricDef def(String code, String baseDim, int slot, String name, String unit) {
        PerfMetricDef d = new PerfMetricDef();
        d.setMetricCode(code);
        d.setMetricName(name);
        d.setBaseDim(baseDim);
        d.setValSlot(slot);
        d.setStatus("ACTIVE");
        d.setUnit(unit);
        return d;
    }

    private static PerfTargetValue targetValue(BigDecimal target) {
        PerfTargetValue tv = new PerfTargetValue();
        tv.setTargetValue(target);
        return tv;
    }
}
