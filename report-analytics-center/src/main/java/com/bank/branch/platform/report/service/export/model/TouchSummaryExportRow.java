package com.bank.branch.platform.report.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 触达汇总导出行（M5.2.3 占位）.
 *
 * <p>对齐 03 §C.2 列定义（empId / empName / orgName / 触达任务数 / 完成率）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TouchSummaryExportRow {

    @ExcelProperty("员工工号")
    private String empId;

    @ExcelProperty("员工姓名")
    private String empName;

    @ExcelProperty("机构名称")
    private String orgName;

    @ExcelProperty("触达任务数")
    private Integer taskCount;

    @ExcelProperty("完成率")
    private String completionRate;
}
