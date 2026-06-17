package com.bank.branch.platform.performance.service.importer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 目标方案导入行模型（2026-06-17，importType=TARGET_PLAN）.
 *
 * <p>对齐「目标方案 Excel 导入模板」（sheet1，第 0 行表头，从第 1 行起数据）：
 * <ol>
 *   <li>序号（透传，不参与业务）</li>
 *   <li>目标方案编号（必填）</li>
 *   <li>目标方案名称（必填）</li>
 *   <li>阶段名称（必填，为 uk_plan_subject_metric_stage 的一部分）</li>
 *   <li>阶段起始日期（yyyyMMdd 整数或真实日期格式 cell）</li>
 *   <li>阶段截止日期（yyyyMMdd 整数或真实日期格式 cell）</li>
 *   <li>维度（中文「员工」「机构」，兼容 EMP/ORG）</li>
 *   <li>工号/部门编号（EMP → 工号；ORG → 部门编号，归一为机构编码）</li>
 *   <li>指标名称（必须存在于 PERF_METRIC_DEF.metric_name）</li>
 *   <li>目标值（必填，可解析 BigDecimal）</li>
 *   <li>基础值（可空）</li>
 * </ol>
 *
 * <p>用 POI 解析而非 EasyExcel：日期列既支持数值 20260101 也支持真实日期 cell，
 * 需在读 cell 时按类型分支（参考 {@code MetricResultImportStrategy} 的 getString/DateUtil）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetPlanImportRow {

    /** Excel 物理行号（仅用于错误消息，1-based，第一行表头）. */
    private int excelRowNum;

    /** 序号（透传，不参与业务）. */
    private String indexNo;

    /** 目标方案编号（必填）. */
    private String planCode;

    /** 目标方案名称（必填）. */
    private String planName;

    /** 阶段名称（必填，uk 的一部分）. */
    private String stageName;

    /** 阶段起始日期. */
    private LocalDate startDate;

    /** 阶段截止日期. */
    private LocalDate endDate;

    /** 维度原始值（中文「员工」「机构」或 EMP/ORG）. */
    private String dimRaw;

    /** 工号/部门编号原始值（EMP=工号 / ORG=部门编号）. */
    private String subjectKey;

    /** 指标名称（中文）. */
    private String metricName;

    /** 目标值（必填）. */
    private BigDecimal targetValue;

    /** 基础值（可空）. */
    private BigDecimal baseValue;
}
