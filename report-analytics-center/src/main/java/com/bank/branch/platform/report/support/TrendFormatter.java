package com.bank.branch.platform.report.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 仪表盘 KPI 卡环比趋势文案格式器（V1.14 # 2 新增）.
 *
 * <p>用于 {@link com.bank.branch.platform.report.service.impl.DashboardServiceImpl} 的
 * {@code buildStats(...)} 组装 5 项 KPI 卡 trend 文案：
 * 比较"当日值"与"月初日值"，按百分比生成 "↑ 较月初 +X.X%" / "↓ 较月初 -X.X%" / "--" 三档展示.
 *
 * <p>缺值/除零保护：{@code curr / prev} 任一为 {@code null}，或 {@code prev} 为 0，
 * 返回 {@code Result("--", "flat")}（前端展示 "--" 占位 + flat 灰色样式）.
 *
 * <p>精度规则：百分比保留 1 位小数，使用 {@link RoundingMode#HALF_UP} 四舍五入.
 */
public final class TrendFormatter {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TrendFormatter() {
        // 工具类禁止实例化
    }

    /**
     * 计算环比趋势文案与类型.
     *
     * @param curr 当日值（可为 null）
     * @param prev 环比基准值，通常是月初日的同 metric 值（可为 null 或 0）
     * @return 二元组 {@link Result}（text=展示文案，type=up/down/flat）
     */
    public static Result format(BigDecimal curr, BigDecimal prev) {
        if (curr == null || prev == null || prev.signum() == 0) {
            return new Result("--", "flat");
        }
        BigDecimal pct = curr.subtract(prev)
                .divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
        int sign = pct.signum();
        if (sign > 0) {
            return new Result("↑ 较月初 +" + pct.toPlainString() + "%", "up");
        }
        if (sign < 0) {
            // pct 已含负号，无需额外拼 "-"
            return new Result("↓ 较月初 " + pct.toPlainString() + "%", "down");
        }
        return new Result("较月初 0.0%", "flat");
    }

    /**
     * 趋势计算结果.
     *
     * @param text 展示文案（如 "↑ 较月初 +3.2%" / "--"）
     * @param type 趋势类型（{@code up} / {@code down} / {@code flat}），前端用作 CSS class
     */
    public record Result(String text, String type) {
    }
}
