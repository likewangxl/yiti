package com.bank.branch.platform.bridge;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerformanceMetricApiBridge 单元测试 —— 纯 JUnit 5 + Mockito，无需 Spring 上下文。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>performance API 返回 null / 空列表 → 桥接返回 emptyList</li>
 *   <li>performance API 返回的列表含 null 元素 → 桥接过滤</li>
 *   <li>6 个公共字段（metricCode/metricName/unit/currentValue/targetValue/achievementRate）正确投影</li>
 *   <li>trend 字段从 mom 推导 4 种语义（UP / DOWN / FLAT / null）</li>
 *   <li>dataDate (LocalDate) → dataTime (LocalDateTime) 走 atStartOfDay</li>
 * </ul>
 */
class PerformanceMetricApiBridgeTest {

    /**
     * performance API 返回 null（防御性，规范上不应出现，但 stub 时易触发）→ 桥接降级返回 emptyList
     */
    @Test
    void shouldReturnEmptyListWhenSourceIsNull() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);
        when(mockApi.getUserMetricCards("E10001")).thenReturn(null);

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).isNotNull().isEmpty();
    }

    /**
     * performance API 返回 emptyList → 桥接保持 empty
     */
    @Test
    void shouldReturnEmptyListWhenSourceIsEmpty() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);
        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.emptyList());

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).isNotNull().isEmpty();
    }

    /**
     * performance API 返回的列表含 null 元素 → 桥接通过 Objects::nonNull 过滤
     */
    @Test
    void shouldFilterNullElementsFromSource() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO valid =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("M001")
                        .metricName("有效卡片")
                        .build();

        when(mockApi.getUserMetricCards("E10001"))
                .thenReturn(Arrays.asList(null, valid, null));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMetricCode()).isEqualTo("M001");
    }

    /**
     * 6 个公共字段（metricCode/metricName/unit/currentValue/targetValue/achievementRate）正确投影
     */
    @Test
    void shouldProjectAllCommonFields() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .metricName("存款余额")
                        .unit("万元")
                        .currentValue(new BigDecimal("123.45"))
                        .targetValue(new BigDecimal("200.00"))
                        .achievementRate(new BigDecimal("61.73"))
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        MetricCardDTO dto = result.get(0);
        assertThat(dto.getMetricCode()).isEqualTo("KPI_001");
        assertThat(dto.getMetricName()).isEqualTo("存款余额");
        assertThat(dto.getUnit()).isEqualTo("万元");
        assertThat(dto.getCurrentValue()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(dto.getTargetValue()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(dto.getAchievementRate()).isEqualByComparingTo(new BigDecimal("61.73"));
    }

    /**
     * mom > 0 → trend = "UP"
     */
    @Test
    void shouldDeriveTrendUpWhenMomPositive() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .mom(new BigDecimal("1.5"))
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTrend()).isEqualTo("UP");
        assertThat(result.get(0).getChangeRate()).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    /**
     * mom < 0 → trend = "DOWN"
     */
    @Test
    void shouldDeriveTrendDownWhenMomNegative() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .mom(new BigDecimal("-2.0"))
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTrend()).isEqualTo("DOWN");
    }

    /**
     * mom == 0 → trend = "FLAT"
     */
    @Test
    void shouldDeriveTrendFlatWhenMomZero() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .mom(BigDecimal.ZERO)
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTrend()).isEqualTo("FLAT");
    }

    /**
     * mom == null → trend = null（保留 null 表示不适用）
     */
    @Test
    void shouldDeriveTrendNullWhenMomNull() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .mom(null)
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTrend()).isNull();
        assertThat(result.get(0).getChangeRate()).isNull();
    }

    /**
     * dataDate (LocalDate) → dataTime (LocalDateTime) 走 atStartOfDay (00:00:00)
     */
    @Test
    void shouldConvertDataDateToLocalDateTime() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        LocalDate srcDate = LocalDate.of(2026, 4, 25);
        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .dataDate(srcDate)
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDataTime())
                .isEqualTo(LocalDateTime.of(2026, 4, 25, 0, 0, 0));
    }

    /**
     * dataDate == null → dataTime = null（保留 null 表示无数据日期）
     */
    @Test
    void shouldKeepDataTimeNullWhenSourceDataDateIsNull() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);

        com.bank.branch.platform.performance.api.dto.MetricCardDTO src =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("KPI_001")
                        .dataDate(null)
                        .build();

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(src));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getUserMetricCards("E10001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDataTime()).isNull();
    }

    @Test
    void shouldUsePersonalCoreMetricQueryAndKeepPreviousValue() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);
        com.bank.branch.platform.performance.api.dto.MetricCardDTO source =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("DEPOSIT_BALANCE")
                        .metricName("对公一般性存款余额")
                        .currentValue(new BigDecimal("120"))
                        .previousValue(new BigDecimal("100"))
                        .mom(new BigDecimal("20"))
                        .dataDate(LocalDate.of(2026, 9, 20))
                        .build();
        when(mockApi.getPersonalCoreMetricCards(" E10001 "))
                .thenReturn(Collections.singletonList(source));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        List<MetricCardDTO> result = bridge.getPersonalCoreMetricCards(" E10001 ");

        assertThat(result).singleElement().satisfies(card -> {
            assertThat(card.getMetricCode()).isEqualTo("DEPOSIT_BALANCE");
            assertThat(card.getPreviousValue()).isEqualByComparingTo("100");
            assertThat(card.getChangeRate()).isEqualByComparingTo("20");
            assertThat(card.getComparisonType()).isEqualTo("PREVIOUS_MONTH_END");
            assertThat(card.getSourceType()).isEqualTo("EMP_LATEST_IMPORT");
            assertThat(card.getDataTime()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0));
        });
        verify(mockApi).getPersonalCoreMetricCards(" E10001 ");
    }

    @Test
    void shouldFailStrictlyWhenPersonalCoreSourceIsNull() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);
        when(mockApi.getPersonalCoreMetricCards("E10001")).thenReturn(null);

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        assertThatThrownBy(() -> bridge.getPersonalCoreMetricCards("E10001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned null");
    }

    @Test
    void shouldLeaveSourceTypeNullForLegacyCards() {
        com.bank.branch.platform.performance.api.MetricApi mockApi =
                mock(com.bank.branch.platform.performance.api.MetricApi.class);
        com.bank.branch.platform.performance.api.dto.MetricCardDTO source =
                com.bank.branch.platform.performance.api.dto.MetricCardDTO.builder()
                        .metricCode("LEGACY")
                        .build();
        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(source));

        PerformanceMetricApiBridge bridge = new PerformanceMetricApiBridge(mockApi);

        assertThat(bridge.getUserMetricCards("E10001")).singleElement()
                .satisfies(card -> assertThat(card.getSourceType()).isNull());
    }
}
