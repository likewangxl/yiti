package com.bank.branch.platform.performance.enums;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class MetricValueTimeEnumTest {

    private final LocalDate base = LocalDate.of(2026, 7, 9); // 2026年3季度中

    @Test
    void bySuffix_matchesEnumOrNull() {
        assertThat(MetricValueTimeEnum.bySuffix("PME")).isEqualTo(MetricValueTimeEnum.PME);
        assertThat(MetricValueTimeEnum.bySuffix("D1")).isEqualTo(MetricValueTimeEnum.D1);
        assertThat(MetricValueTimeEnum.bySuffix("PQE")).isEqualTo(MetricValueTimeEnum.PQE);
        assertThat(MetricValueTimeEnum.bySuffix("PYE")).isEqualTo(MetricValueTimeEnum.PYE);
        assertThat(MetricValueTimeEnum.bySuffix("XXX")).isNull();
        assertThat(MetricValueTimeEnum.bySuffix(null)).isNull();
    }

    @Test
    void resolve_returnsExpectedAnchorDates() {
        assertThat(MetricValueTimeEnum.TODAY.resolve(base)).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(MetricValueTimeEnum.D1.resolve(base)).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(MetricValueTimeEnum.PME.resolve(base)).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(MetricValueTimeEnum.PQE.resolve(base)).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(MetricValueTimeEnum.PYE.resolve(base)).isEqualTo(LocalDate.of(2025, 12, 31));
    }
}
