package com.bank.branch.platform.performance.service.importer.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标定义 Excel 导入行模型.
 *
 * <p>与 {@code docs/指标表上传模板.xlsx} 列头一一对应。
 * 字段值直接与数据库对齐，不再做数字翻译。
 */
@Data
@NoArgsConstructor
public class MetricDefImportRow {

    @ExcelProperty("指标序号")
    private Integer indexNo;

    @ExcelProperty(value = "指标层级(2级支行由1级支行计算而来、后面还可以细化到3级指标)")
    private Integer metricLevel;

    @ExcelProperty("指标名称")
    private String metricName;

    @ExcelProperty("指标编号")
    private String metricCode;

    @ExcelProperty("基础维度（EMP/ORG/CUST）")
    private String baseDim;

    @ExcelProperty("指标分类")
    private String metricCategory;

    /** 计算方式：AUTO / MANUAL */
    @ExcelProperty("计算方式")
    private String calcMode;

    @ExcelProperty("计算规则(SQL或自定义规则)")
    private String calcRule;

    /** 指标状态：ACTIVE / DISABLED */
    @ExcelProperty("指标状态(勾选后可以在报表中查询、未勾选时不进行显示)")
    private String status;
}
