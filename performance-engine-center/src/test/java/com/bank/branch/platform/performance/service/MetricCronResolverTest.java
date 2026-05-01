package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricCronResolverTest {

    private final MetricCronResolver resolver = new MetricCronResolver();

    @Test
    void cron_expr_priority_over_calc_freq() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCronExpr("0 30 5 * * ?");
        def.setCalcFreq("MONTH");
        assertThat(resolver.resolve(def)).isEqualTo("0 30 5 * * ?");
    }

    @Test
    void default_cron_for_DAY() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCalcFreq("DAY");
        assertThat(resolver.resolve(def)).isEqualTo("0 0 2 * * ?");
    }

    @Test
    void default_cron_for_WEEK() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCalcFreq("WEEK");
        assertThat(resolver.resolve(def)).isEqualTo("0 0 2 ? * MON");
    }

    @Test
    void default_cron_for_MONTH() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCalcFreq("MONTH");
        assertThat(resolver.resolve(def)).isEqualTo("0 0 2 1 * ?");
    }

    @Test
    void default_cron_for_QUARTER() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCalcFreq("QUARTER");
        assertThat(resolver.resolve(def)).isEqualTo("0 0 2 1 1,4,7,10 ?");
    }

    @Test
    void default_cron_for_YEAR() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCalcFreq("YEAR");
        assertThat(resolver.resolve(def)).isEqualTo("0 0 2 1 1 ?");
    }

    @Test
    void unknown_calc_freq_throws() {
        PerfMetricDef def = new PerfMetricDef();
        def.setCalcFreq("HOURLY");
        assertThatThrownBy(() -> resolver.resolve(def))
            .isInstanceOf(PerfException.class)
            .satisfies(ex -> {
                String code = ((PerfException) ex).getCode();
                assertThat(code).isEqualTo("PERF-40021");
            });
    }
}
