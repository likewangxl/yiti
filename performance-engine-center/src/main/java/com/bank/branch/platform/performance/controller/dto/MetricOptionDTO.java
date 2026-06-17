package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 指标下拉项 DTO（KPI 计算结果详情页"指标"下拉，仅含该方案的指标）.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricOptionDTO {

    /** 指标编码（下拉 value）. */
    private String metricCode;

    /** 指标名称（下拉 label）. */
    private String metricName;

    /** 该 KPI 指标的权重（PERF_KPI_ITEM.weight）；结果详情页「权重」列展示用，下拉场景可忽略. */
    private BigDecimal weight;
}
