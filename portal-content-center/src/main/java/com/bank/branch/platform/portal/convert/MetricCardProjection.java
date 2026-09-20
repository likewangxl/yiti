package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.adapter.dto.MetricCardDTO;
import com.bank.branch.platform.portal.api.dto.PortalMetricCard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MetricCardDTO -> PortalMetricCard 字段投影转换器
 *
 * <p>将 {@link MetricCardDTO} 中的 BigDecimal 数值字段格式化为两位小数字符串，
 * 并将 {@code achievementRate} 重命名为 {@code completionRate}。</p>
 */
public final class MetricCardProjection {

    private MetricCardProjection() {}

    /**
     * 将 MetricCardDTO 转换为门户使用的 PortalMetricCard
     *
     * @param src 源 MetricCardDTO，允许为 null
     * @return 转换后的 PortalMetricCard，src 为 null 时返回 null
     */
    public static PortalMetricCard toPortal(MetricCardDTO src) {
        if (src == null) return null;
        return PortalMetricCard.builder()
                .metricCode(src.getMetricCode())
                .metricName(src.getMetricName())
                .unit(src.getUnit())
                .trend(src.getTrend())
                .currentValue(formatNumber(src.getCurrentValue()))
                .previousValue(formatNumber(src.getPreviousValue()))
                .targetValue(formatNumber(src.getTargetValue()))
                .completionRate(src.getAchievementRate()) // 字段重命名
                .changeRate(src.getChangeRate())
                .comparisonType(src.getComparisonType())
                .sourceType(src.getSourceType())
                .dataTime(src.getDataTime())
                .build();
    }

    /**
     * 将 BigDecimal 格式化为两位小数的字符串
     *
     * @param v 数值，允许为 null
     * @return 格式化后的字符串，null 时返回空字符串
     */
    private static String formatNumber(BigDecimal v) {
        if (v == null) return "";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
