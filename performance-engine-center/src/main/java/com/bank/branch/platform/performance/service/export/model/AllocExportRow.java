package com.bank.branch.platform.performance.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 分配关系导出 Excel 行模型（V1.2 Task Q6.3）.
 *
 * <p>与 {@code cust_alloc_relation} 表字段对齐（选取主要业务字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocExportRow {

    @ExcelProperty("分配关系 ID")
    private String id;

    @ExcelProperty("客户 ID")
    private String custId;

    @ExcelProperty("分配维度")
    private String allocDim;

    @ExcelProperty("业务种类")
    private String bizKind;

    @ExcelProperty("账号")
    private String accountNo;

    @ExcelProperty("员工工号")
    private String empId;

    @ExcelProperty("分配比例")
    private BigDecimal ratio;

    @ExcelProperty("生效日期")
    private LocalDate effectiveDate;

    @ExcelProperty("失效日期")
    private LocalDate endDate;
}
