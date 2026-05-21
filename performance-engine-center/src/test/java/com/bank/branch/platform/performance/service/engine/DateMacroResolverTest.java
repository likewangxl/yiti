package com.bank.branch.platform.performance.service.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DateMacroResolver 单元测试（6 case：闰年/季初/季末/年初/普通日/null）.
 *
 * <p>覆盖 spec docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md §2 边界用例.
 */
class DateMacroResolverTest {

    @Test
    void resolve_normalDay_returnsAllEightMacros() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 5, 20));
        assertThat(m).containsOnlyKeys(
                "dateToday", "dateYesterday",
                "dateMonthEnd", "datePrevMonthEnd",
                "dateQuarterEnd", "datePrevQuarterEnd",
                "dateYearEnd", "datePrevYearEnd");
        assertThat(m).containsEntry("dateToday", LocalDate.of(2026, 5, 20));
        assertThat(m).containsEntry("dateYesterday", LocalDate.of(2026, 5, 19));
        assertThat(m).containsEntry("dateMonthEnd", LocalDate.of(2026, 5, 31));
        assertThat(m).containsEntry("datePrevMonthEnd", LocalDate.of(2026, 4, 30));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2026, 6, 30));
        assertThat(m).containsEntry("datePrevQuarterEnd", LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("dateYearEnd", LocalDate.of(2026, 12, 31));
        assertThat(m).containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_leapYearFeb29_handlesMonthBoundary() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2024, 2, 29));
        assertThat(m).containsEntry("dateMonthEnd", LocalDate.of(2024, 2, 29));
        assertThat(m).containsEntry("datePrevMonthEnd", LocalDate.of(2024, 1, 31));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2024, 3, 31));
        assertThat(m).containsEntry("dateYearEnd", LocalDate.of(2024, 12, 31));
    }

    @Test
    void resolve_q1MidJan_prevQuarterCrossesYear() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 1, 15));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("datePrevQuarterEnd", LocalDate.of(2025, 12, 31));
        assertThat(m).containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_quarterLastDay_dateQuarterEndEqualsToday() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("datePrevQuarterEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_yearFirstDay_yesterdayCrossesYear() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 1, 1));
        assertThat(m).containsEntry("dateYesterday", LocalDate.of(2025, 12, 31));
        assertThat(m).containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
        assertThat(m).containsEntry("datePrevMonthEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_null_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> DateMacroResolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataDate");
    }
}
