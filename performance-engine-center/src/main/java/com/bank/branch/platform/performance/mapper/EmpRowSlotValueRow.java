package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EMP 宽表按已选定行 ID、多个 slot 投影的强类型结果。
 */
@Data
public class EmpRowSlotValueRow {

    /** 宽表行主键。 */
    private Long rowId;

    /** 宽表数据日期。 */
    private LocalDate dataDate;

    /** 值槽编号。 */
    private Integer slot;

    /** 指标值；NULL 表示该行该 slot 未产出。 */
    private BigDecimal metricValue;
}
