package com.bank.branch.platform.portal.adapter.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标趋势数据点 —— 工作台趋势图使用。
 *
 * @deprecated V1 临时占位，待 performance-engine-center 模块创建后迁移
 */
@Deprecated
@Data
public class MetricTrendPoint {

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 指标值 */
    private BigDecimal value;
}
