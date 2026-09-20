package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门户工作台使用的绩效指标卡片 DTO（不可变对象）
 *
 * <p>由 {@link com.bank.branch.platform.portal.convert.MetricCardProjection}
 * 从 {@link com.bank.branch.platform.portal.adapter.dto.MetricCardDTO} 投影而来，
 * 仅保留前端工作台卡片渲染所需的字段。</p>
 */
@Value
@Builder
public class PortalMetricCard {

    /** 指标编码 */
    String metricCode;

    /** 指标名称 */
    String metricName;

    /** 当前值（格式化后的字符串，保留两位小数） */
    String currentValue;

    /** 上月末值（格式化后的字符串，保留两位小数） */
    String previousValue;

    /** 目标值（格式化后的字符串，保留两位小数） */
    String targetValue;

    /** 达成率（百分比） */
    BigDecimal completionRate;

    /** 趋势方向（UP / DOWN / FLAT） */
    String trend;

    /** 环比变化率 */
    BigDecimal changeRate;

    /** 比较口径（如 PREVIOUS_MONTH_END）；旧来源为空 */
    String comparisonType;

    /** 数据来源口径（如 EMP_LATEST_IMPORT）；旧来源为空 */
    String sourceType;

    /** 指标数据时间 */
    LocalDateTime dataTime;

    /** 单位（元 / 笔 / % 等） */
    String unit;
}
