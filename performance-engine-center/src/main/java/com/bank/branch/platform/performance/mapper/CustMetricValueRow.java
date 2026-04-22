package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户指标值查询行模型.
 *
 * <p>{@link CustIndexResultMapper#selectSlotValuesByCusts} 返回的投影：
 * 每行携带 custId 与该客户在指定 slot 上的 metricValue。
 */
@Data
public class CustMetricValueRow {

    /** 客户ID. */
    private String custId;

    /** 指标值（对应宽表 val_{slot} 列；为 null 表示该 slot 未赋值）. */
    private BigDecimal metricValue;
}
