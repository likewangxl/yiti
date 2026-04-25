package com.bank.branch.platform.report.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绩效汇总导出行（M5.2.3 占位）.
 *
 * <p>对齐 03 §C.3 列定义（empId / empName / cycleType / cycleKey / score / rank）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerfSummaryExportRow {

    @ExcelProperty("员工工号")
    private String empId;

    @ExcelProperty("员工姓名")
    private String empName;

    @ExcelProperty("周期类型")
    private String cycleType;

    @ExcelProperty("周期")
    private String cycleKey;

    @ExcelProperty("总分")
    private String score;

    @ExcelProperty("名次")
    private Integer rank;
}
