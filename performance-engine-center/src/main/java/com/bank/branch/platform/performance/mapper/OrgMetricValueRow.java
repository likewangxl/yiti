package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 机构指标值查询行模型.
 *
 * <p>{@link OrgIndexResultMapper#selectSlotValuesByOrgs} 返回的投影：
 * 每行携带 orgCode 与该机构在指定 slot 上的 metricValue。
 */
@Data
public class OrgMetricValueRow {

    /** 机构编码. */
    private String orgCode;

    /** 指标值（对应宽表 val_{slot} 列；为 null 表示该 slot 未赋值）. */
    private BigDecimal metricValue;
}
