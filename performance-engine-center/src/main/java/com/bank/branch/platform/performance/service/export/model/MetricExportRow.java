package com.bank.branch.platform.performance.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 指标结果导出 Excel 行模型（V1.2 Task Q6.3）.
 *
 * <p>宽表 200 slot 按 "每行 = 一个 (subjectId, metricCode, value)" 扁平化，
 * 供 Excel 导出使用。subjectId 可以是员工工号或机构编码，由 baseDim 指明.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricExportRow {

    @ExcelProperty("对象维度")
    private String baseDim;

    @ExcelProperty("对象 ID")
    private String subjectId;

    @ExcelProperty("指标编码")
    private String metricCode;

    @ExcelProperty("指标名称")
    private String metricName;

    @ExcelProperty("数据日期")
    private LocalDate dataDate;

    @ExcelProperty("数据版本")
    private String dataVersion;

    @ExcelProperty("指标值")
    private BigDecimal metricValue;
}
