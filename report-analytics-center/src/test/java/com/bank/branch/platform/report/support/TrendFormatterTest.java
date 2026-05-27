package com.bank.branch.platform.report.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TrendFormatter} 守护测试（V1.14 # 2 Step 4 RED）.
 *
 * <p>覆盖 5 类边界：
 * <ol>
 *   <li>{@code curr > prev}：上升趋势 "↑ 较月初 +X.X%" type=up</li>
 *   <li>{@code curr < prev}：下降趋势 "↓ 较月初 -X.X%" type=down</li>
 *   <li>{@code curr == prev}：持平 type=flat</li>
 *   <li>{@code curr/prev null} 或 {@code prev == 0}：降级 "--" type=flat（避免除零）</li>
 *   <li>百分比保留 1 位小数（HALF_UP）</li>
 * </ol>
 */
class TrendFormatterTest {

    @Test
    void format_currGtPrev_shouldReturnUpArrowWithPositivePct() {
        TrendFormatter.Result r = TrendFormatter.format(new BigDecimal("110"), new BigDecimal("100"));
        assertThat(r.text()).isEqualTo("↑ 较月初 +10.0%");
        assertThat(r.type()).isEqualTo("up");
    }

    @Test
    void format_currLtPrev_shouldReturnDownArrowWithNegativePct() {
        TrendFormatter.Result r = TrendFormatter.format(new BigDecimal("90"), new BigDecimal("100"));
        assertThat(r.text()).isEqualTo("↓ 较月初 -10.0%");
        assertThat(r.type()).isEqualTo("down");
    }

    @Test
    void format_currEqPrev_shouldReturnFlat() {
        TrendFormatter.Result r = TrendFormatter.format(new BigDecimal("100"), new BigDecimal("100"));
        assertThat(r.type()).isEqualTo("flat");
    }

    @Test
    void format_currNull_shouldReturnDashFlat() {
        TrendFormatter.Result r = TrendFormatter.format(null, new BigDecimal("100"));
        assertThat(r.text()).isEqualTo("--");
        assertThat(r.type()).isEqualTo("flat");
    }

    @Test
    void format_prevNull_shouldReturnDashFlat() {
        TrendFormatter.Result r = TrendFormatter.format(new BigDecimal("100"), null);
        assertThat(r.text()).isEqualTo("--");
        assertThat(r.type()).isEqualTo("flat");
    }

    @Test
    void format_prevZero_shouldReturnDashFlat() {
        TrendFormatter.Result r = TrendFormatter.format(new BigDecimal("100"), BigDecimal.ZERO);
        assertThat(r.text()).isEqualTo("--");
        assertThat(r.type()).isEqualTo("flat");
    }

    @Test
    void format_pct_shouldKeepOneDecimal_halfUp() {
        // 103.456 - 100 = 3.456, /100 = 3.456%，保留 1 位 (HALF_UP) → 3.5
        TrendFormatter.Result r = TrendFormatter.format(new BigDecimal("103.456"), new BigDecimal("100"));
        assertThat(r.type()).isEqualTo("up");
        assertThat(r.text()).isEqualTo("↑ 较月初 +3.5%");
    }
}
