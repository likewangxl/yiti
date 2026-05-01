package com.bank.branch.platform.performance.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CalcFreqEnumTest {

    @Test
    void five_values_present() {
        assertThat(CalcFreqEnum.values()).hasSize(5);
    }

    @Test
    void isValid_accepts_uppercase() {
        assertThat(CalcFreqEnum.isValid("DAY")).isTrue();
        assertThat(CalcFreqEnum.isValid("WEEK")).isTrue();
        assertThat(CalcFreqEnum.isValid("MONTH")).isTrue();
        assertThat(CalcFreqEnum.isValid("QUARTER")).isTrue();
        assertThat(CalcFreqEnum.isValid("YEAR")).isTrue();
    }

    @Test
    void isValid_rejects_unknown() {
        assertThat(CalcFreqEnum.isValid("HOURLY")).isFalse();
        assertThat(CalcFreqEnum.isValid(null)).isFalse();
        assertThat(CalcFreqEnum.isValid("")).isFalse();
    }

    @Test
    void isValid_case_insensitive() {
        assertThat(CalcFreqEnum.isValid("day")).isTrue();
        assertThat(CalcFreqEnum.isValid("Month")).isTrue();
    }
}
