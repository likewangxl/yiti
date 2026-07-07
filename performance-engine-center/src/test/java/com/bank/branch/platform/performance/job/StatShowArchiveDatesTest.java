package com.bank.branch.platform.performance.job;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** StatShowArchiveDates 纯日期逻辑单测：旬路由 / 旬首 / 边界清理区间 / 瘦身月 / 区间遍历。 */
class StatShowArchiveDatesTest {

    @Test
    void histSuffix_routesByThird() {
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 1))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 10))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 11))).isEqualTo("_H3");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 20))).isEqualTo("_H3");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 21))).isEqualTo("_H1");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 31))).isEqualTo("_H1");
    }

    @Test
    void sliceStart_returnsThirdStart() {
        assertThat(StatShowArchiveDates.sliceStart(LocalDate.of(2026, 8, 5))).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(StatShowArchiveDates.sliceStart(LocalDate.of(2026, 8, 15))).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(StatShowArchiveDates.sliceStart(LocalDate.of(2026, 8, 25))).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void boundaryCleanup_on11_cleansPrevMonth1To10_H2() {
        var opt = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 11));
        assertThat(opt).isPresent();
        var cr = opt.get();
        assertThat(cr.histSuffix()).isEqualTo("_H2");
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    void boundaryCleanup_on21_cleansPrevMonth11To20_H3() {
        var cr = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 21)).orElseThrow();
        assertThat(cr.histSuffix()).isEqualTo("_H3");
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void boundaryCleanup_on1_cleansTwoMonthsAgo21ToEnd_H1() {
        var cr = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 1)).orElseThrow();
        assertThat(cr.histSuffix()).isEqualTo("_H1");
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void boundaryCleanup_on1_march_handlesFebEnd() {
        var cr = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 3, 1)).orElseThrow();
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 1, 21));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void boundaryCleanup_onNonBoundary_empty() {
        assertThat(StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 15))).isEmpty();
        assertThat(StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 2))).isEmpty();
    }

    @Test
    void pruneMonth_on1_returnsPrevMonthFullRange() {
        var r = StatShowArchiveDates.pruneMonth(LocalDate.of(2026, 8, 1)).orElseThrow();
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void pruneMonth_on1_jan_handlesYearRollover() {
        var r = StatShowArchiveDates.pruneMonth(LocalDate.of(2026, 1, 1)).orElseThrow();
        assertThat(r.start()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void pruneMonth_onNon1_empty() {
        assertThat(StatShowArchiveDates.pruneMonth(LocalDate.of(2026, 8, 11))).isEmpty();
    }

    @Test
    void datesInclusive_returnsClosedIntervalAscending() {
        List<LocalDate> ds = StatShowArchiveDates.datesInclusive(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 1));
        assertThat(ds).containsExactly(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 7, 30), LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 1));
    }

    @Test
    void fmt_isIsoLocalDate() {
        assertThat(StatShowArchiveDates.fmt(LocalDate.of(2026, 7, 5))).isEqualTo("2026-07-05");
    }
}
