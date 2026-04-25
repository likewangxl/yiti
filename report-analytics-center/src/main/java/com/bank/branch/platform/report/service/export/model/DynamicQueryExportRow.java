package com.bank.branch.platform.report.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态查询导出行（M5.2.3 占位）.
 *
 * <p>V1.0 用最小化字段集承载 EasyExcel 列定义。M6+ 接入 DynamicQueryService.query
 * 后再细化为多指标动态宽表（参考 03 §A.2 行结构）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicQueryExportRow {

    @ExcelProperty("对象ID")
    private String subjectId;

    @ExcelProperty("对象名称")
    private String subjectName;

    @ExcelProperty("数据日期")
    private String dataDate;

    @ExcelProperty("指标编码")
    private String metricCode;

    @ExcelProperty("指标值")
    private String metricValue;
}
