package com.bank.branch.platform.performance.service.importer.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 目标值 Excel 导入行模型（V1.1 Task P5.2）.
 *
 * <p>与 C.4 / D.2 Excel 模板列头一一对应，由 {@code EasyExcel.read} 反序列化。
 * 由 easyexcel 通过 @ExcelProperty 的字符串值（而非 value index）匹配列头；
 * 因此 Excel 首行列名必须完全匹配，否则视为列头不对齐（由策略统一抛
 * {@code IMPORT_COLUMN_MAPPING_INVALID (PERF-42203)}）。
 *
 * <p>字段口径：
 * <ul>
 *   <li>{@code targetPlanCode} —— 目标方案业务编码（由 Service 映射为 planId）</li>
 *   <li>{@code empId} —— 员工工号，subject_type 固定为 EMP</li>
 *   <li>{@code metricCode} —— 指标编码（perf_metric_def）</li>
 *   <li>{@code targetValue} —— 目标值 decimal(20,4)</li>
 *   <li>{@code remark} —— 备注（可空，暂不持久化，保留用于错误日志回显）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class TargetImportRow {

    /** 目标方案编码. */
    @ExcelProperty("目标方案编码")
    private String targetPlanCode;

    /** 员工编号. */
    @ExcelProperty("员工编号")
    private String empId;

    /** 指标编码. */
    @ExcelProperty("指标编码")
    private String metricCode;

    /** 目标值. */
    @ExcelProperty("目标值")
    private BigDecimal targetValue;

    /** 备注（可空）. */
    @ExcelProperty("备注")
    private String remark;
}
