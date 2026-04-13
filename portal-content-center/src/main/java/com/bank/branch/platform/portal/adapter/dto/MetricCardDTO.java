package com.bank.branch.platform.portal.adapter.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标卡片 DTO —— 工作台指标概览使用。
 *
 * @deprecated V1 临时占位，待 performance-engine-center 模块创建后迁移
 */
@Deprecated
@Data
public class MetricCardDTO {

    /** 指标编码 */
    private String metricCode;

    /** 指标名称 */
    private String metricName;

    /** 当前值 */
    private BigDecimal currentValue;

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

    /** 统计周期（DAY / WEEK / MONTH / QUARTER / YEAR） */
    private String period;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 前端颜色提示（green / red / grey） */
    private String colorHint;
}
