package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import com.bank.branch.platform.portal.api.dto.PortalMetricCard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * MetricAdapter 单元测试 —— 纯 JUnit 5 + Mockito，无需 Spring 上下文
 */
class MetricAdapterTest {

    /**
     * V1 阶段 MetricApi bean 不存在（null），应降级返回空列表
     */
    @Test
    void shouldReturnEmptyListWhenMetricApiBeanIsNull() {
        // given: MetricAdapter 无 MetricApi bean（默认 null）
        MetricAdapter adapter = new MetricAdapter();

        // when
        List<PortalMetricCard> result = adapter.fetch("E10001");

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    /**
     * MetricApi bean 存在时，应正确调用并投影 MetricCardDTO -> PortalMetricCard
     */
    @Test
    void shouldProjectMetricCardDTOToPortalMetricCardWhenBeanExists() {
        // given
        MetricAdapter adapter = new MetricAdapter();
        MetricApi mockApi = mock(MetricApi.class);
        adapter.setMetricApi(mockApi);

        MetricCardDTO dto = new MetricCardDTO();
        dto.setMetricCode("KPI_001");
        dto.setMetricName("存款余额");
        dto.setCurrentValue(new BigDecimal("123.456"));
        dto.setTargetValue(new BigDecimal("200.009"));
        dto.setAchievementRate(new BigDecimal("61.73"));
        dto.setUnit("万元");
        dto.setTrend("UP");

        when(mockApi.getUserMetricCards("E10001")).thenReturn(Collections.singletonList(dto));

        // when
        List<PortalMetricCard> result = adapter.fetch("E10001");

        // then
        assertThat(result).hasSize(1);
        PortalMetricCard card = result.get(0);
        assertThat(card.getMetricCode()).isEqualTo("KPI_001");
        assertThat(card.getMetricName()).isEqualTo("存款余额");
        assertThat(card.getCurrentValue()).isEqualTo("123.46"); // 四舍五入到两位小数
        assertThat(card.getTargetValue()).isEqualTo("200.01");  // 四舍五入到两位小数
        assertThat(card.getCompletionRate()).isEqualByComparingTo(new BigDecimal("61.73"));
        assertThat(card.getUnit()).isEqualTo("万元");
        assertThat(card.getTrend()).isEqualTo("UP");

        verify(mockApi).getUserMetricCards("E10001");
    }

    /**
     * MetricApi 抛出异常时，应优雅降级返回空列表而非传播异常
     */
    @Test
    void shouldReturnEmptyListWhenApiThrows() {
        // given
        MetricAdapter adapter = new MetricAdapter();
        MetricApi mockApi = mock(MetricApi.class);
        adapter.setMetricApi(mockApi);

        when(mockApi.getUserMetricCards("E10001"))
                .thenThrow(new RuntimeException("远程服务不可用"));

        // when
        List<PortalMetricCard> result = adapter.fetch("E10001");

        // then
        assertThat(result).isNotNull().isEmpty();
        verify(mockApi).getUserMetricCards("E10001");
    }

    @Test
    void shouldThrowWhenMetricApiBeanIsMissingForWorkspace() {
        MetricAdapter adapter = new MetricAdapter();

        assertThatThrownBy(() -> adapter.fetchForWorkspace("E10001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void shouldPropagateMetricApiFailureForWorkspace() {
        MetricAdapter adapter = new MetricAdapter();
        MetricApi mockApi = mock(MetricApi.class);
        adapter.setMetricApi(mockApi);
        when(mockApi.getPersonalCoreMetricCards("E10001"))
                .thenThrow(new RuntimeException("远程服务不可用"));

        assertThatThrownBy(() -> adapter.fetchForWorkspace("E10001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldUsePersonalCoreMetricQueryForWorkspace() {
        MetricAdapter adapter = new MetricAdapter();
        MetricApi mockApi = mock(MetricApi.class);
        adapter.setMetricApi(mockApi);
        MetricCardDTO dto = new MetricCardDTO();
        dto.setMetricCode("DEPOSIT_BALANCE");
        dto.setPreviousValue(new BigDecimal("100"));
        dto.setCurrentValue(new BigDecimal("120"));
        dto.setComparisonType("PREVIOUS_MONTH_END");
        dto.setSourceType("EMP_LATEST_IMPORT");
        when(mockApi.getPersonalCoreMetricCards("E10001"))
                .thenReturn(Collections.singletonList(dto));

        List<PortalMetricCard> result = adapter.fetchForWorkspace("E10001");

        assertThat(result).singleElement().satisfies(card ->
                assertThat(card.getPreviousValue()).isEqualTo("100.00"));
        assertThat(result.get(0).getComparisonType()).isEqualTo("PREVIOUS_MONTH_END");
        assertThat(result.get(0).getSourceType()).isEqualTo("EMP_LATEST_IMPORT");
        verify(mockApi).getPersonalCoreMetricCards("E10001");
        verify(mockApi, never()).getUserMetricCards("E10001");
    }
}
