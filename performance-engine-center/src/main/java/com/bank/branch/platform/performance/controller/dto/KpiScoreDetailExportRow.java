package com.bank.branch.platform.performance.controller.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 「导出KPI明细数据」Excel 行（PERF_KPI_SCORE 平铺记录）.
 */
@Data
public class KpiScoreDetailExportRow {

    @ExcelProperty("数据日期")
    private String dataDate;

    @ExcelProperty("方案编码")
    private String schemeCode;

    @ExcelProperty("维度")
    private String dimLabel;

    @ExcelProperty("对象ID")
    private String subjectId;

    @ExcelProperty("姓名")
    private String subjectName;

    @ExcelProperty("指标编码")
    private String metricCode;

    @ExcelProperty("指标名称")
    private String metricName;

    @ExcelProperty("实际值")
    private BigDecimal actualValue;

    @ExcelProperty("目标值")
    private BigDecimal targetValue;

    @ExcelProperty("基础值")
    private BigDecimal baseValue;

    @ExcelProperty("权重")
    private BigDecimal weight;

    @ExcelProperty("得分")
    private BigDecimal score;
}
