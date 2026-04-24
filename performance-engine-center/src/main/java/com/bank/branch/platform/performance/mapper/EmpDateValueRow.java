package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * V1.5 P4.1：{@link EmpIndexResultMapper#selectSlotValuesByDatesRaw} 返回行投影.
 *
 * <p>一行携带 dataDate 与该日期在指定 slot 上的 metricValue（即 {@code val_{slot}} 列值）。
 *
 * <p>放在 mapper 包（而非 entity 包），强调其为"读取视图"而非数据库实体；
 * 与 {@link EmpMetricValueRow} 对称，区别仅投影维度（前者是 dataDate，后者是 empId）。
 */
@Data
public class EmpDateValueRow {

    /** 数据日期. */
    private LocalDate dataDate;

    /** 指标值（对应宽表 val_{slot} 列；为 null 表示该 slot 未赋值）. */
    private BigDecimal metricValue;
}
