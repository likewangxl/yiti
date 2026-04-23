package com.bank.branch.platform.performance.service.export.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * KPI 明细导出 Excel 行模型（V1.2 Task Q6.3）.
 *
 * <p>每条 {@code kpi_result.detail_json} 包含 items 数组（单项 KPI 明细），
 * 导出时把每个 item 拆成一行：（KPI 主记录字段 + item 细节字段）。
 *
 * <p>detail_json 结构示例：
 * <pre>
 * {
 *   "items": [
 *     {"itemCode":"M_DEP_AVG", "metricValue":100, "targetValue":120, "weight":0.3, "score":25},
 *     {"itemCode":"M_LOAN_NPL", "metricValue":0.8, "targetValue":0.5, "weight":0.2, "score":16}
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDetailExportRow {

    @ExcelProperty("员工编号")
    private String empId;

    @ExcelProperty("周期类型")
    private String cycleType;

    @ExcelProperty("周期日期")
    private LocalDate cycleDate;

    @ExcelProperty("基准日")
    private LocalDate asOfDate;

    @ExcelProperty("KPI 总分")
    private BigDecimal kpiTotalScore;

    @ExcelProperty("指标编码")
    private String itemCode;

    @ExcelProperty("指标实绩")
    private BigDecimal metricValue;

    @ExcelProperty("目标值")
    private BigDecimal targetValue;

    @ExcelProperty("权重")
    private BigDecimal weight;

    @ExcelProperty("项目得分")
    private BigDecimal itemScore;
}
