package com.bank.branch.platform.performance.job;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** StatShowArchiveDates 纯日期逻辑：运行日旬路由及指标 dataDate 路由。 */
class StatShowArchiveDatesTest {

    @Test
    void histSuffixForRunDate_routesByRunDay() {
        assertThat(StatShowArchiveDates.histSuffixForRunDate(LocalDate.of(2026, 8, 1))).isEqualTo("_H1");
        assertThat(StatShowArchiveDates.histSuffixForRunDate(LocalDate.of(2026, 8, 10))).isEqualTo("_H1");
        assertThat(StatShowArchiveDates.histSuffixForRunDate(LocalDate.of(2026, 8, 11))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffixForRunDate(LocalDate.of(2026, 8, 20))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffixForRunDate(LocalDate.of(2026, 8, 21))).isEqualTo("_H3");
        assertThat(StatShowArchiveDates.histSuffixForRunDate(LocalDate.of(2026, 8, 31))).isEqualTo("_H3");
    }

    @Test
    void histSuffixForDataDate_routesUsingTheFollowingRunDate() {
        assertThat(StatShowArchiveDates.histSuffixForDataDate(LocalDate.of(2026, 7, 31))).isEqualTo("_H1");
        assertThat(StatShowArchiveDates.histSuffixForDataDate(LocalDate.of(2026, 8, 10))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffixForDataDate(LocalDate.of(2026, 8, 20))).isEqualTo("_H3");
    }

    @Test
    void histSuffix_isRunDateAliasForCompatibility() {
        LocalDate runDate = LocalDate.of(2026, 8, 11);
        assertThat(StatShowArchiveDates.histSuffix(runDate))
                .isEqualTo(StatShowArchiveDates.histSuffixForRunDate(runDate));
    }

    @Test
    void boundaryRunDate_isOnlyFirstEleventhAndTwentyFirst() {
        assertThat(StatShowArchiveDates.isBoundaryRunDate(LocalDate.of(2026, 8, 1))).isTrue();
        assertThat(StatShowArchiveDates.isBoundaryRunDate(LocalDate.of(2026, 8, 11))).isTrue();
        assertThat(StatShowArchiveDates.isBoundaryRunDate(LocalDate.of(2026, 8, 21))).isTrue();
        assertThat(StatShowArchiveDates.isBoundaryRunDate(LocalDate.of(2026, 8, 10))).isFalse();
        assertThat(StatShowArchiveDates.isBoundaryRunDate(LocalDate.of(2026, 8, 12))).isFalse();
    }

    @Test
    void fmt_isIsoLocalDate() {
        assertThat(StatShowArchiveDates.fmt(LocalDate.of(2026, 7, 5))).isEqualTo("2026-07-05");
    }
}
