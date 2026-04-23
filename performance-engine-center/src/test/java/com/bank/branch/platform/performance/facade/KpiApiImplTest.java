package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KpiApiImpl 单元测试.
 *
 * <p>覆盖 spec §5.2.3 DoD:
 * <ul>
 *   <li>2 个 V1.0 实现方法 (getKpiScheme/getKpiSchemeById) 的存在/不存在分支</li>
 *   <li>V1.0 定型时 3 个 V1.1 占位方法曾统一抛 "V1.1 delivered"，V1.1 交付后
 *       实际已实现或改为 "V1.2 delivered"（本文件断言不再依赖具体 message）</li>
 * </ul>
 *
 * <p>Facade 纯 Mock 测试, 不启动 Spring 容器; {@code @Cacheable} 由 Spring AOP 在集成测试中验证,
 * 本层只断言 Service 交互 + DTO assemble 正确性。
 */
class KpiApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private KpiResultMapper kpiResultMapper;

    @InjectMocks
    private KpiApiImpl kpiApi;

    // ------------------------- V1.0 实现: getKpiSchemeById -------------------------

    @Test
    @DisplayName("getKpiSchemeById: 存在时返回含 items 的 DTO")
    void getKpiSchemeById_whenExists_returnsDto() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("FACADE_A");
        scheme.setId("S_FACADE_A");
        PerfKpiItem it1 = KpiTestDataBuilder.item("S_FACADE_A", "TEST_KPI_FACADE_M1", new BigDecimal("40.0000"));
        PerfKpiItem it2 = KpiTestDataBuilder.item("S_FACADE_A", "TEST_KPI_FACADE_M2", new BigDecimal("60.0000"));
        when(kpiSchemeService.getByIdOrNull("S_FACADE_A")).thenReturn(Optional.of(scheme));
        when(kpiItemService.listBySchemeId("S_FACADE_A")).thenReturn(List.of(it1, it2));

        Optional<KpiSchemeDTO> dto = kpiApi.getKpiSchemeById("S_FACADE_A");

        assertThat(dto).isPresent();
        KpiSchemeDTO value = dto.get();
        assertThat(value.getId()).isEqualTo("S_FACADE_A");
        assertThat(value.getSchemeCode()).isEqualTo("TEST_KPI_FACADE_A");
        assertThat(value.getCycleType()).isEqualTo("MONTHLY");
        assertThat(value.getStatus()).isEqualTo("ACTIVE");
        assertThat(value.getOpenDetail()).isFalse();
        assertThat(value.getItems()).hasSize(2);
        assertThat(value.getItems())
                .extracting("metricCode", "weight")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TEST_KPI_FACADE_M1", new BigDecimal("40.0000")),
                        org.assertj.core.groups.Tuple.tuple("TEST_KPI_FACADE_M2", new BigDecimal("60.0000"))
                );
    }

    @Test
    @DisplayName("getKpiSchemeById: 不存在时返回 Optional.empty 且不查 items")
    void getKpiSchemeById_whenNotExists_returnsEmpty() {
        when(kpiSchemeService.getByIdOrNull("NO_SUCH")).thenReturn(Optional.empty());

        assertThat(kpiApi.getKpiSchemeById("NO_SUCH")).isEmpty();
        verifyNoInteractions(kpiItemService);
    }

    // ------------------------- V1.0 实现: getKpiScheme (by code) -------------------------

    @Test
    @DisplayName("getKpiScheme: 按 code 存在时返回含 items 的 DTO")
    void getKpiScheme_whenExists_returnsDto() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("FACADE_CODE");
        scheme.setId("S_FACADE_CODE");
        scheme.setOpenDetail(1);
        PerfKpiItem it1 = KpiTestDataBuilder.item("S_FACADE_CODE", "TEST_KPI_FACADE_CM");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_KPI_FACADE_CODE")).thenReturn(Optional.of(scheme));
        when(kpiItemService.listBySchemeId("S_FACADE_CODE")).thenReturn(List.of(it1));

        Optional<KpiSchemeDTO> dto = kpiApi.getKpiScheme("TEST_KPI_FACADE_CODE");

        assertThat(dto).isPresent();
        KpiSchemeDTO value = dto.get();
        assertThat(value.getSchemeCode()).isEqualTo("TEST_KPI_FACADE_CODE");
        assertThat(value.getOpenDetail()).isTrue();
        assertThat(value.getItems()).hasSize(1);
        assertThat(value.getItems().get(0).getMetricCode()).isEqualTo("TEST_KPI_FACADE_CM");
    }

    @Test
    @DisplayName("getKpiScheme: 不存在时返回 Optional.empty")
    void getKpiScheme_whenNotExists_returnsEmpty() {
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_KPI_NO_SUCH")).thenReturn(Optional.empty());

        assertThat(kpiApi.getKpiScheme("TEST_KPI_NO_SUCH")).isEmpty();
        verifyNoInteractions(kpiItemService);
    }

    @Test
    @DisplayName("getKpiScheme: 方案无 item 时返回 items 为空列表")
    void getKpiScheme_whenNoItems_returnsEmptyItems() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("NO_ITEM");
        scheme.setId("S_NO_ITEM");
        when(kpiSchemeService.getBySchemeCodeOrNull("TEST_KPI_NO_ITEM")).thenReturn(Optional.of(scheme));
        when(kpiItemService.listBySchemeId("S_NO_ITEM")).thenReturn(Collections.emptyList());

        Optional<KpiSchemeDTO> dto = kpiApi.getKpiScheme("TEST_KPI_NO_ITEM");

        assertThat(dto).isPresent();
        assertThat(dto.get().getItems()).isEmpty();
        verify(kpiItemService).listBySchemeId("S_NO_ITEM");
    }

    // ------------------------- V1.1 契约: 查询方法（细分场景见 KpiApiImplV11Test） -------------------------
}
