package com.bank.branch.platform.portal.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门户工作台使用的绩效指标卡片 DTO
 *
 * <p>由 {@link com.bank.branch.platform.portal.convert.MetricCardProjection}
 * 从 {@link com.bank.branch.platform.portal.adapter.dto.MetricCardDTO} 投影而来，
 * 仅保留前端工作台卡片渲染所需的 7 个字段。</p>
 */
@Data
public class PortalMetricCard {

    /** 指标编码 */
    private String metricCode;

    /** 指标名称 */
    private String metricName;

    /** 当前值（格式化后的字符串，保留两位小数） */
    private String currentValue;

    /** 目标值（格式化后的字符串，保留两位小数） */
    private String targetValue;

    /** 达成率（百分比） */
    private BigDecimal completionRate;

    /** 趋势方向（UP / DOWN / FLAT） */
    private String trend;

    /** 单位（元 / 笔 / % 等） */
    private String unit;
}
