package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TargetApiImpl 单元测试.
 *
 * <p>覆盖 Plan Task 3.3 (plan L1386-1396) DoD:
 * <ul>
 *   <li>4 个 V1.0 实现方法 (getTargetPlan/getTargetPlanById/getTargetValue/listTargetValues)</li>
 *   <li>每方法正反向 2 个场景, 共 8 UT</li>
 * </ul>
 *
 * <p>纯 Mock 测试, 不启动 Spring 容器; {@code @Cacheable} 行为由 IT 在 Spring AOP 层验证,
 * 本层只断言 Service 交互 + Assembler 映射正确性。
 *
 * <p>planId 全 String (v1.2 约定), 测试编码前缀 {@code TEST_TGT_}。
 */
class TargetApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private TargetPlanService targetPlanService;

    @Mock
    private TargetValueService targetValueService;

    @InjectMocks
    private TargetApiImpl targetApi;

    // ------------------------- getTargetPlanById (按 id, 带 @Cacheable) -------------------------

    @Test
    @DisplayName("getTargetPlanById: 存在时返回 DTO")
    void getTargetPlanById_whenExists_returnsDto() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("FACADE_ID", "KPI_SCHEME_1");
        plan.setId("P_FACADE_ID");
        when(targetPlanService.getByIdOrNull("P_FACADE_ID")).thenReturn(Optional.of(plan));

        Optional<TargetPlanDTO> dto = targetApi.getTargetPlanById("P_FACADE_ID");

        assertThat(dto).isPresent();
        TargetPlanDTO value = dto.get();
        assertThat(value.getId()).isEqualTo("P_FACADE_ID");
        assertThat(value.getPlanCode()).isEqualTo("TEST_TGT_FACADE_ID");
        assertThat(value.getPlanName()).isEqualTo("测试目标方案-FACADE_ID");
        assertThat(value.getKpiSchemeId()).isEqualTo("KPI_SCHEME_1");
        assertThat(value.getTargetDim()).isEqualTo("EMP");
        assertThat(value.getTargetCycle()).isEqualTo("YEAR");
        assertThat(value.getStatus()).isEqualTo("ACTIVE");
        assertThat(value.getEffectiveDate()).isEqualTo(plan.getEffectiveDate());
    }

    @Test
    @DisplayName("getTargetPlanById: 不存在时返回 Optional.empty")
    void getTargetPlanById_whenNotExists_returnsEmpty() {
        when(targetPlanService.getByIdOrNull("NO_SUCH")).thenReturn(Optional.empty());

        assertThat(targetApi.getTargetPlanById("NO_SUCH")).isEmpty();
        verifyNoInteractions(targetValueService);
    }

    // ------------------------- getTargetPlan (按 code, 不缓存) -------------------------

    @Test
    @DisplayName("getTargetPlan: 按 code 存在时返回 DTO")
    void getTargetPlan_whenExists_returnsDto() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("FACADE_CODE", "KPI_SCHEME_2");
        plan.setId("P_FACADE_CODE");
        when(targetPlanService.getByCodeOrNull("TEST_TGT_FACADE_CODE")).thenReturn(Optional.of(plan));

        Optional<TargetPlanDTO> dto = targetApi.getTargetPlan("TEST_TGT_FACADE_CODE");

        assertThat(dto).isPresent();
        assertThat(dto.get().getId()).isEqualTo("P_FACADE_CODE");
        assertThat(dto.get().getPlanCode()).isEqualTo("TEST_TGT_FACADE_CODE");
        assertThat(dto.get().getKpiSchemeId()).isEqualTo("KPI_SCHEME_2");
    }

    @Test
    @DisplayName("getTargetPlan: 不存在时返回 Optional.empty")
    void getTargetPlan_whenNotExists_returnsEmpty() {
        when(targetPlanService.getByCodeOrNull("TEST_TGT_NO_SUCH")).thenReturn(Optional.empty());

        assertThat(targetApi.getTargetPlan("TEST_TGT_NO_SUCH")).isEmpty();
        verifyNoInteractions(targetValueService);
    }

    // ------------------------- getTargetValue (单值, 不缓存) -------------------------

    @Test
    @DisplayName("getTargetValue: UK 命中时返回 target_value")
    void getTargetValue_returnsOptional_whenFound() {
        PerfTargetValue v = TargetTestDataBuilder.value(
                "P_FACADE_ID", "EMP", "E001", "2026Q1",
                "TEST_TGT_M1", new BigDecimal("12000.0000"));
        when(targetValueService.getByUniqueKey(
                "P_FACADE_ID", "EMP", "E001", "2026Q1", "TEST_TGT_M1"))
                .thenReturn(Optional.of(v));

        Optional<BigDecimal> result = targetApi.getTargetValue(
                "P_FACADE_ID", "EMP", "E001", "2026Q1", "TEST_TGT_M1");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("12000.0000");
    }

    @Test
    @DisplayName("getTargetValue: UK 未命中时返回 Optional.empty")
    void getTargetValue_returnsEmpty_whenNotFound() {
        when(targetValueService.getByUniqueKey(
                "P_FACADE_ID", "EMP", "E001", "2026Q1", "TEST_TGT_NO_M"))
                .thenReturn(Optional.empty());

        assertThat(targetApi.getTargetValue(
                "P_FACADE_ID", "EMP", "E001", "2026Q1", "TEST_TGT_NO_M"))
                .isEmpty();
        verifyNoInteractions(targetPlanService);
    }

    // ------------------------- listTargetValues (某主体某周期, 不缓存) -------------------------

    @Test
    @DisplayName("listTargetValues: 无数据时返回空列表")
    void listTargetValues_whenNoData_returnsEmpty() {
        when(targetValueService.listByPlan(
                eq("P_FACADE_ID"), eq("EMP"), eq("E001"), eq("2026Q1"),
                anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 0L, Collections.emptyList()));

        List<TargetValueDTO> result = targetApi.listTargetValues(
                "P_FACADE_ID", "EMP", "E001", "2026Q1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listTargetValues: 按主体+周期返回装配后的 DTO 列表")
    void listTargetValues_returnsList() {
        PerfTargetValue v1 = TargetTestDataBuilder.value(
                "P_FACADE_ID", "EMP", "E001", "2026Q1",
                "TEST_TGT_M1", new BigDecimal("100.0000"));
        v1.setId("V_1");
        PerfTargetValue v2 = TargetTestDataBuilder.value(
                "P_FACADE_ID", "EMP", "E001", "2026Q1",
                "TEST_TGT_M2", new BigDecimal("200.0000"));
        v2.setId("V_2");
        when(targetValueService.listByPlan(
                eq("P_FACADE_ID"), eq("EMP"), eq("E001"), eq("2026Q1"),
                anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 2L, List.of(v1, v2)));

        List<TargetValueDTO> result = targetApi.listTargetValues(
                "P_FACADE_ID", "EMP", "E001", "2026Q1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TargetValueDTO::getId, TargetValueDTO::getPlanId,
                        TargetValueDTO::getSubjectType, TargetValueDTO::getSubjectId,
                        TargetValueDTO::getCycleKey, TargetValueDTO::getMetricCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("V_1", "P_FACADE_ID",
                                "EMP", "E001", "2026Q1", "TEST_TGT_M1"),
                        org.assertj.core.groups.Tuple.tuple("V_2", "P_FACADE_ID",
                                "EMP", "E001", "2026Q1", "TEST_TGT_M2"));
        assertThat(result).extracting(TargetValueDTO::getTargetValue)
                .containsExactly(new BigDecimal("100.0000"), new BigDecimal("200.0000"));
    }
}
