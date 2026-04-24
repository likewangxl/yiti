package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 指标卡片 DTO (工作台展示).
 * <p>V1.0 仅定义结构, V1.1 由 MetricApi.getUserMetricCards 填充数据.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricCardDTO {
    /** 指标编码. */
    private String metricCode;
    /** 指标名称. */
    private String metricName;
    /** 当前值. */
    private BigDecimal currentValue;
    /** 上期值 (同比/环比参考). */
    private BigDecimal previousValue;
    /** 当前周期目标. */
    private BigDecimal targetValue;
    /** 基础值. */
    private BigDecimal baseValue;
    /** 达成率 (%). */
    private BigDecimal achievementRate;
    /** 单位 (万元/笔/人等). */
    private String unit;
    /** 排序号. */
    private Integer sortNo;
    /** 数据日期. */
    private LocalDate dataDate;
    /** V1.4 S3.1 新增: 环比变化率 (%, 两位小数, 保留 null 表示不适用/上期为 0/未命中). */
    private BigDecimal mom;
    /** V1.4 S3.1 新增: 同比变化率 (%, 两位小数, 保留 null 表示不适用/去年同期为 0/未命中). */
    private BigDecimal yoy;
}
