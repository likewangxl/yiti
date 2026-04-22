package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工指标值查询行模型.
 *
 * <p>{@link EmpIndexResultMapper#selectSlotValuesByEmps} 返回的投影类型：
 * 每行携带 empId 与该员工在指定 slot 上的 metricValue（即 {@code val_{slot}} 列值）。
 *
 * <p>放在 mapper 包（而非 entity 包），强调其为"读取视图"而非数据库实体。
 */
@Data
public class EmpMetricValueRow {

    /** 员工工号. */
    private String empId;

    /** 指标值（对应宽表 val_{slot} 列；为 null 表示该 slot 未赋值）. */
    private BigDecimal metricValue;
}
