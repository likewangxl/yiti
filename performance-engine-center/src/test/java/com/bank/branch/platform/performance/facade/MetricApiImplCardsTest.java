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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

        // actual
        when(empIndexResultMapper.selectSlotValue(eq(empId), eq(latest), eq("v1"), eq(5)))
                .thenReturn(new BigDecimal("80"));
        when(empIndexResultMapper.selectSlotValue(eq(empId), eq(latest), eq("v1"), eq(6)))
                .thenReturn(new BigDecimal("50"));

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
        when(empIndexResultMapper.selectSlotValue(eq(empId), eq(latest), eq("v1"), eq(5)))
                .thenReturn(new BigDecimal("10"));
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
        when(empIndexResultMapper.selectSlotValue(eq(empId), eq(latest), eq("v1"), eq(5)))
                .thenReturn(new BigDecimal("10"));
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
        when(empIndexResultMapper.selectSlotValue(eq(empId), eq(latest), eq("v1"), eq(1)))
                .thenReturn(new BigDecimal("5"));
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
        when(empIndexResultMapper.selectSlotValue(eq(empId), any(), eq("v1"), eq(5)))
                .thenReturn(new BigDecimal("10"));
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
