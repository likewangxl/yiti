package com.bank.branch.platform.performance.service.importer.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标定义 Excel 导入行模型（V1.9 Task M1）.
 *
 * <p>与 {@code docs/指标表上传模板.xlsx} 的 9 列列头一一对应，由 {@code EasyExcel.read}
 * 反序列化。{@code @ExcelProperty} 按字符串值匹配列头。
 *
 * <p>字段口径：
 * <ul>
 *   <li>{@code indexNo} —— 指标序号（同时作为 val_slot 与兜底 metric_code 来源）</li>
 *   <li>{@code metricLevel} —— 指标层级：1=一级支行可计算；2=二级派生；3=三级</li>
 *   <li>{@code metricName} —— 指标名称（必填）</li>
 *   <li>{@code metricCode} —— 指标编号；空则按 {@code M_{indexNo:04d}} 自动生成（全表唯一约束由 DB 保障）</li>
 *   <li>{@code metricCategory} —— 指标分类（规模类/效益类/质量类/合规类等），存到新增列 metric_category</li>
 *   <li>{@code sourceType} —— 指标来源：1=外部导入；2=系统提取；3=计算</li>
 *   <li>{@code calcRule} —— 计算规则（SQL 或自定义表达式），来源=1 时可空</li>
 *   <li>{@code scheduleType} —— 定时任务：1=每日 2=每月 3=每季 4=每年</li>
 *   <li>{@code statusFlag} —— 指标状态：1=ACTIVE 0=DISABLED</li>
 * </ul>
 *
 * <p>所有数值列在 Excel 端为整型，使用 Integer 接收以便 null 与 0 区分。
 */
@Data
@NoArgsConstructor
public class MetricDefImportRow {

    /** 指标序号. */
    @ExcelProperty("指标序号")
    private Integer indexNo;

    /** 指标层级 (1/2/3). */
    @ExcelProperty(value = "指标层级(2级支行由1级支行计算而来、后面还可以细化到3级指标)")
    private Integer metricLevel;

    /** 指标名称. */
    @ExcelProperty("指标名称")
    private String metricName;

    /** 指标编号（空则自动生成）. */
    @ExcelProperty("指标编号")
    private String metricCode;

    /** 指标分类. */
    @ExcelProperty("指标分类")
    private String metricCategory;

    /** 指标来源：1=外部导入 2=系统提取 3=计算. */
    @ExcelProperty(value = "指标来源(可以是外部导入、可以是系统从总行数据库中提取、可以是通过提取数据进行的计算)")
    private Integer sourceType;

    /** 计算规则（SQL 或自定义）. */
    @ExcelProperty("计算规则(SQL或自定义规则)")
    private String calcRule;

    /** 定时任务：1=每日 2=每月 3=每季 4=每年. */
    @ExcelProperty("定时任务(每日、每月、每季、每年)")
    private Integer scheduleType;

    /** 指标状态：1=ACTIVE 0=DISABLED. */
    @ExcelProperty("指标状态(勾选后可以在报表中查询、未勾选时不进行显示)")
    private Integer statusFlag;
}
