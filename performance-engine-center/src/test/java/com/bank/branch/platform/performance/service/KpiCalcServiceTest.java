package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiCalcService 单元测试（Task P4.2 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>单员工 KPI 计算：按 weight 加权得 total_score，写 kpi_result + detailJson</li>
 *   <li>方案不存在：抛 KPI_SCHEME_NOT_FOUND</li>
 *   <li>方案无 item：total_score = 0，detailJson 空 items</li>
 *   <li>metric 未分配 val_slot：抛 METRIC_CALC_LOGIC_INVALID</li>
 *   <li>metric 值为 null（宽表无此员工数据）：metricValue 默认按 0 参与计算</li>
 *   <li>weight 总和 = 0：抛 KPI_WEIGHT_SUM_INVALID</li>
 *   <li>max/min score 裁剪：item 得分超上限被 clamp</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KpiCalcServiceTest {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private EmpIndexResultMapper empIndexResultMapper;

    @Mock
    private KpiResultMapper kpiResultMapper;

    @Mock
    private KpiFormulaService kpiFormulaService;

    @InjectMocks
    private KpiCalcService kpiCalcService;

    @Test
    @DisplayName("calcSingleEmp：按 weight 加权 + 写 kpi_result + detailJson 含 items")
    void calcSingleEmp_basicWeightedSum() {
        String empId = "E001";
        String schemeCode = "TEST_KPI_A";
        LocalDate cycleDate = LocalDate.of(2026, 3, 31);
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        String version = "v20260401";

        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_A");
        scheme.setSchemeCode(schemeCode);
        scheme.setSchemeName("方案A");
        scheme.setCycleType("MONTHLY");
        scheme.setStatus("ACTIVE");
        when(kpiSchemeService.getBySchemeCodeOrNull(schemeCode)).thenReturn(Optional.of(scheme));

        PerfKpiItem it1 = new PerfKpiItem();
        it1.setId("IT_A1");
        it1.setSchemeId("S_A");
        it1.setMetricCode("M_EMP_A");
        it1.setWeight(new BigDecimal("30.0000"));
        it1.setMultiplier(new BigDecimal("1.0000"));
        it1.setMinScore(new BigDecimal("0.0000"));
        it1.setMaxScore(new BigDecimal("100.0000"));

        PerfKpiItem it2 = new PerfKpiItem();
        it2.setId("IT_A2");
        it2.setSchemeId("S_A");
        it2.setMetricCode("M_EMP_B");
        it2.setWeight(new BigDecimal("70.0000"));
        it2.setMultiplier(new BigDecimal("1.0000"));
        it2.setMinScore(new BigDecimal("0.0000"));
        it2.setMaxScore(new BigDecimal("100.0000"));

        when(kpiItemService.listBySchemeId("S_A")).thenReturn(List.of(it1, it2));

        PerfMetricDef defA = new PerfMetricDef();
        defA.setMetricCode("M_EMP_A");
        defA.setValSlot(1);
        defA.setBaseDim("EMP");
        defA.setStatus("ACTIVE");

        PerfMetricDef defB = new PerfMetricDef();
        defB.setMetricCode("M_EMP_B");
        defB.setValSlot(2);
        defB.setBaseDim("EMP");
        defB.setStatus("ACTIVE");

        when(metricDefService.getByCodeOrNull("M_EMP_A")).thenReturn(defA);
        when(metricDefService.getByCodeOrNull("M_EMP_B")).thenReturn(defB);

        when(empIndexResultMapper.selectSlotValue(eq("E001"), eq(asOfDate), eq(version), eq(1)))
                .thenReturn(new BigDecimal("100"));
        when(empIndexResultMapper.selectSlotValue(eq("E001"), eq(asOfDate), eq(version), eq(2)))
                .thenReturn(new BigDecimal("200"));

        // 公式服务返回：item1 = 100 * 1 = 100；item2 = 200 * 1 = 200
        when(kpiFormulaService.eval(anyString(), any())).thenReturn(
                new BigDecimal("100"), new BigDecimal("200"));

        Long id = kpiCalcService.calcSingleEmp(empId, schemeCode, "MONTHLY",
                cycleDate, asOfDate, version);

        // 不依赖自动回填 id（Mockito 不会真插）：这里只断言 Mapper.insert 被调且 kpi_result 字段正确
        ArgumentCaptor<KpiResult> captor = ArgumentCaptor.forClass(KpiResult.class);
        verify(kpiResultMapper).insert(captor.capture());
        KpiResult saved = captor.getValue();

        // 100 cap=100 → 100；200 cap=100 → 100（上限裁剪），totalScore = (100*30 + 100*70)/(30+70) = 100
        assertThat(saved.getKpiTotalScore()).isEqualByComparingTo("100");
        assertThat(saved.getEmpId()).isEqualTo("E001");
        assertThat(saved.getCycleType()).isEqualTo("MONTHLY");
        assertThat(saved.getCycleDate()).isEqualTo(cycleDate);
        assertThat(saved.getAsOfDate()).isEqualTo(asOfDate);
        assertThat(saved.getDataVersion()).isEqualTo(version);
        assertThat(saved.getDetailJson()).contains("M_EMP_A", "M_EMP_B");
    }

    @Test
    @DisplayName("calcSingleEmp：方案不存在抛 KPI_SCHEME_NOT_FOUND")
    void calcSingleEmp_schemeNotFound_throws() {
        when(kpiSchemeService.getBySchemeCodeOrNull("NOT_EXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kpiCalcService.calcSingleEmp("E001", "NOT_EXIST",
                "MONTHLY", LocalDate.now(), LocalDate.now(), "v1"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.KPI_SCHEME_NOT_FOUND);
        verify(kpiResultMapper, never()).insert(any(KpiResult.class));
    }

    @Test
    @DisplayName("calcSingleEmp：方案无 item → weight 总和=0 抛 KPI_WEIGHT_SUM_INVALID")
    void calcSingleEmp_noItems_throwsWeightSumInvalid() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_EMPTY");
        scheme.setSchemeCode("TEST_EMPTY");
        scheme.setStatus("ACTIVE");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_EMPTY")).thenReturn(Optional.of(scheme));
        when(kpiItemService.listBySchemeId("S_EMPTY")).thenReturn(List.of());

        assertThatThrownBy(() -> kpiCalcService.calcSingleEmp("E001", "TEST_EMPTY",
                "MONTHLY", LocalDate.now(), LocalDate.now(), "v1"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.KPI_WEIGHT_SUM_INVALID);
    }

    @Test
    @DisplayName("calcSingleEmp：metric 未分配 slot 抛 METRIC_CALC_LOGIC_INVALID")
    void calcSingleEmp_metricSlotMissing_throws() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_NOSLOT");
        scheme.setSchemeCode("TEST_NOSLOT");
        scheme.setStatus("ACTIVE");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_NOSLOT")).thenReturn(Optional.of(scheme));
        PerfKpiItem it = new PerfKpiItem();
        it.setSchemeId("S_NOSLOT");
        it.setMetricCode("M_NO_SLOT");
        it.setWeight(new BigDecimal("100"));
        it.setMultiplier(new BigDecimal("1"));
        it.setMinScore(BigDecimal.ZERO);
        it.setMaxScore(new BigDecimal("100"));
        when(kpiItemService.listBySchemeId("S_NOSLOT")).thenReturn(List.of(it));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_NO_SLOT");
        def.setValSlot(null);
        def.setStatus("ACTIVE");
        when(metricDefService.getByCodeOrNull("M_NO_SLOT")).thenReturn(def);

        assertThatThrownBy(() -> kpiCalcService.calcSingleEmp("E001", "TEST_NOSLOT",
                "MONTHLY", LocalDate.now(), LocalDate.now(), "v1"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("calcSingleEmp：metric 不存在抛 METRIC_NOT_FOUND")
    void calcSingleEmp_metricNotFound_throws() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_MNF");
        scheme.setSchemeCode("TEST_MNF");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_MNF")).thenReturn(Optional.of(scheme));
        PerfKpiItem it = new PerfKpiItem();
        it.setMetricCode("NO_METRIC");
        it.setWeight(new BigDecimal("50"));
        it.setMultiplier(new BigDecimal("1"));
        it.setMinScore(BigDecimal.ZERO);
        it.setMaxScore(new BigDecimal("100"));
        when(kpiItemService.listBySchemeId("S_MNF")).thenReturn(List.of(it));
        when(metricDefService.getByCodeOrNull("NO_METRIC")).thenReturn(null);

        assertThatThrownBy(() -> kpiCalcService.calcSingleEmp("E001", "TEST_MNF",
                "MONTHLY", LocalDate.now(), LocalDate.now(), "v1"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_NOT_FOUND);
    }

    @Test
    @DisplayName("calcSingleEmp：宽表 metric 值为 null → 按 0 参与计算")
    void calcSingleEmp_metricValueNull_defaultsToZero() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_NULL");
        scheme.setSchemeCode("TEST_NULL");
        scheme.setStatus("ACTIVE");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_NULL")).thenReturn(Optional.of(scheme));

        PerfKpiItem it = new PerfKpiItem();
        it.setSchemeId("S_NULL");
        it.setMetricCode("M_NULL");
        it.setWeight(new BigDecimal("100"));
        it.setMultiplier(new BigDecimal("1"));
        it.setMinScore(BigDecimal.ZERO);
        it.setMaxScore(new BigDecimal("100"));
        when(kpiItemService.listBySchemeId("S_NULL")).thenReturn(List.of(it));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_NULL");
        def.setValSlot(3);
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        when(metricDefService.getByCodeOrNull("M_NULL")).thenReturn(def);

        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(), anyString(), eq(3)))
                .thenReturn(null);
        // 公式服务传入的变量值应当是 0（Service 层兜底），返回 0
        when(kpiFormulaService.eval(anyString(), any())).thenReturn(BigDecimal.ZERO);

        LocalDate d = LocalDate.of(2026, 4, 1);
        kpiCalcService.calcSingleEmp("E001", "TEST_NULL", "MONTHLY", d, d, "v1");

        ArgumentCaptor<KpiResult> captor = ArgumentCaptor.forClass(KpiResult.class);
        verify(kpiResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getKpiTotalScore()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("calcSingleEmp：item 得分超 max_score 被 clamp")
    void calcSingleEmp_clampsAtMaxScore() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S_CLAMP");
        scheme.setSchemeCode("TEST_CLAMP");
        scheme.setStatus("ACTIVE");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_CLAMP")).thenReturn(Optional.of(scheme));

        PerfKpiItem it = new PerfKpiItem();
        it.setSchemeId("S_CLAMP");
        it.setMetricCode("M_CLAMP");
        it.setWeight(new BigDecimal("100"));
        it.setMultiplier(new BigDecimal("1.0000"));
        it.setMinScore(new BigDecimal("0"));
        it.setMaxScore(new BigDecimal("80"));  // 上限 80
        when(kpiItemService.listBySchemeId("S_CLAMP")).thenReturn(List.of(it));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_CLAMP");
        def.setValSlot(5);
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        when(metricDefService.getByCodeOrNull("M_CLAMP")).thenReturn(def);
        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(), anyString(), eq(5)))
                .thenReturn(new BigDecimal("150"));
        // formula 返回 150（应被 clamp 到 80）
        when(kpiFormulaService.eval(anyString(), any())).thenReturn(new BigDecimal("150"));

        LocalDate d = LocalDate.of(2026, 4, 1);
        kpiCalcService.calcSingleEmp("E001", "TEST_CLAMP", "MONTHLY", d, d, "v1");

        ArgumentCaptor<KpiResult> captor = ArgumentCaptor.forClass(KpiResult.class);
        verify(kpiResultMapper).insert(captor.capture());
        // totalScore = 80 * 100 / 100 = 80
        assertThat(captor.getValue().getKpiTotalScore()).isEqualByComparingTo("80");
    }
}
