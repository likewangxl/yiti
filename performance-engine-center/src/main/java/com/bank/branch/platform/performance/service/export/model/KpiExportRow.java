package com.bank.branch.platform.performance.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 结果导出 Excel 行模型（V1.2 Task Q6.2）.
 *
 * <p>由 easyexcel 通过 {@link ExcelProperty} 写入 Excel（列头按字段顺序生成）。
 *
 * <p>字段口径与 {@code kpi_result} 表对齐：
 * <ul>
 *   <li>{@code empId} —— 员工工号</li>
 *   <li>{@code cycleType} —— 周期类型（MONTHLY / QUARTERLY）</li>
 *   <li>{@code cycleDate} —— 周期日期</li>
 *   <li>{@code asOfDate} —— 计算基准日</li>
 *   <li>{@code dataVersion} —— 数据版本</li>
 *   <li>{@code kpiTotalScore} —— KPI 总分</li>
 *   <li>{@code calculatedTime} —— 计算时间</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiExportRow {

    /** 员工工号. */
    @ExcelProperty("员工编号")
    private String empId;

    /** 周期类型. */
    @ExcelProperty("周期类型")
    private String cycleType;

    /** 周期日期. */
    @ExcelProperty("周期日期")
    private LocalDate cycleDate;

    /** 基准日. */
    @ExcelProperty("基准日")
    private LocalDate asOfDate;

    /** 数据版本. */
    @ExcelProperty("数据版本")
    private String dataVersion;

    /** KPI 总分. */
    @ExcelProperty("KPI 总分")
    private BigDecimal kpiTotalScore;

    /** 计算时间. */
    @ExcelProperty("计算时间")
    private LocalDateTime calculatedTime;
}
