package com.bank.branch.platform.portal.adapter.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门户工作台指标卡片视图层 DTO（防腐层）。
 *
 * <p>由 {@code PerformanceMetricApiBridge} 从
 * {@code com.bank.branch.platform.performance.api.dto.MetricCardDTO} 投影而来，
 * trend 字段由 {@code performance.mom} 推导：
 * &gt;0 UP / =0 FLAT / &lt;0 DOWN / null null。</p>
 */
@Data
public class MetricCardDTO {

    /** 指标编码 */
    private String metricCode;

    /** 指标名称 */
    private String metricName;

    /** 当前值 */
    private BigDecimal currentValue;

    /** 上月末值，用于个人核心指标卡片参考 */
    private BigDecimal previousValue;

    /** 目标值 */
    private BigDecimal targetValue;

    /** 达成率（百分比） */
    private BigDecimal achievementRate;

    /** 单位（元 / 笔 / % 等） */
    private String unit;

    /** 趋势方向（UP / DOWN / FLAT） */
    private String trend;

    /** 环比变化率 */
    private BigDecimal changeRate;

    /** 比较口径（如 PREVIOUS_MONTH_END）；旧来源为空 */
    private String comparisonType;

    /** 数据来源口径（如 EMP_LATEST_IMPORT）；旧来源为空 */
    private String sourceType;

    /** 统计周期（DAY / WEEK / MONTH / QUARTER / YEAR） */
    private String period;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 前端颜色提示（green / red / grey） */
    private String colorHint;
}
