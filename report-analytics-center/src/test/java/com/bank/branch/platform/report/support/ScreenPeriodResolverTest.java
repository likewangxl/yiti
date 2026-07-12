package com.bank.branch.platform.report.support;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScreenPeriodResolver 周期解析单测（固定 today=2026-07-12 消除时间不确定性）.
 */
class ScreenPeriodResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    @Test
    void resolve_nullOrLatest_latestOnly() {
        var p1 = ScreenPeriodResolver.resolve(null, null, null, TODAY);
        assertThat(p1.latestOnly()).isTrue();
        assertThat(p1.from()).isEqualTo(TODAY);
        assertThat(p1.to()).isEqualTo(TODAY);
        var p2 = ScreenPeriodResolver.resolve("LATEST", null, null, TODAY);
        assertThat(p2.latestOnly()).isTrue();
    }

    @Test
    void resolve_last10d_nineDaysBack() {
        var p = ScreenPeriodResolver.resolve("LAST_10D", null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(p.to()).isEqualTo(TODAY);
        assertThat(p.latestOnly()).isFalse();
        assertThat(p.eomOnly()).isFalse();
    }

    @Test
    void resolve_last1m_oneMonthBack() {
        var p = ScreenPeriodResolver.resolve("LAST_1M", null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(p.to()).isEqualTo(TODAY);
    }

    @Test
    void resolve_last6mEom_firstDayOfMonthSixMonthsBack_eomOnly() {
        var p = ScreenPeriodResolver.resolve("LAST_6M_EOM", null, null, TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(p.to()).isEqualTo(TODAY);
        assertThat(p.eomOnly()).isTrue();
    }

    @Test
    void resolve_range_usesGivenDates() {
        var p = ScreenPeriodResolver.resolve("RANGE", "2026-01-01", "2026-03-31", TODAY);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void resolve_rangeMissingOrInverted_throws43011() {
        // 周期参数非法（RANGE 缺 from/to、日期倒挂、格式错）属入参校验失败 → 43011，与执行失败 43008 分离
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("RANGE", null, "2026-01-01", TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43011");
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("RANGE", "2026-02-01", "2026-01-01", TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43011");
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("RANGE", "bad-date", "2026-01-01", TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43011");
    }

    @Test
    void resolve_unknownPeriod_throws43011() {
        // 未知 period 同属周期参数非法 → 43011
        assertThatThrownBy(() -> ScreenPeriodResolver.resolve("LAST_100Y", null, null, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43011");
    }
}
