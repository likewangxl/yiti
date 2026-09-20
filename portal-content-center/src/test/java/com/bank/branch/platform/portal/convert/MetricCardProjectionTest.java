package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import com.bank.branch.platform.portal.api.dto.PortalMetricCard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MetricCardProjectionTest {

    @Test
    void shouldKeepChangeRateAndDataTimeWhenProjectingMetricCard() {
        MetricCardDTO source = new MetricCardDTO();
        source.setMetricCode("KPI_001");
        source.setPreviousValue(new BigDecimal("100.00"));
        source.setComparisonType("PREVIOUS_MONTH_END");
        source.setSourceType("EMP_LATEST_IMPORT");
        source.setChangeRate(new BigDecimal("12.50"));
        source.setDataTime(LocalDateTime.of(2026, 9, 20, 0, 0));

        PortalMetricCard result = MetricCardProjection.toPortal(source);

        assertThat(result.getChangeRate()).isEqualByComparingTo("12.50");
        assertThat(result.getPreviousValue()).isEqualTo("100.00");
        assertThat(result.getComparisonType()).isEqualTo("PREVIOUS_MONTH_END");
        assertThat(result.getSourceType()).isEqualTo("EMP_LATEST_IMPORT");
        assertThat(result.getDataTime()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0));
    }
}
