package com.bank.branch.platform.performance.service.importer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 指标结果导入行模型（V1.12，importType=METRIC_RESULT）.
 *
 * <p>对齐 {@code docs/指标结果模板.xlsx}（长格式 5 列固定）：
 * <ol>
 *   <li>序号（仅展示用，不参与业务）</li>
 *   <li>基础维度：EMP / ORG / CUST / 空（null）</li>
 *   <li>维度对象：员工号 / 机构号 / 客户编号</li>
 *   <li>指标名称（必须存在于 PERF_METRIC_DEF.metric_name）</li>
 *   <li>指标数值</li>
 * </ol>
 *
 * <p>{@code dataDate} 不在行模型中：整文件 dataDate 由 HTTP 参数注入
 * {@link com.bank.branch.platform.performance.service.importer.ImportContext}，
 * Strategy 取 ctx.dataDate() 与所有行共用（V1.12 微调，2026-05-19）。
 *
 * <p>用 POI 而非 EasyExcel 解析的原因：模板为多 Sheet 长格式，Sheet 名作错误定位用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricResultImportRow {

    /** 所在 Sheet 名（仅用于错误定位）. */
    private String sheetName;

    /** Excel 物理行号（仅用于错误消息，1-based，第一行表头）. */
    private int excelRowNum;

    /** 序号（透传，不参与业务）. */
    private String indexNo;

    /** 基础维度：EMP / ORG / CUST / null. */
    private String baseDim;

    /** 维度对象的字符串值（员工号 / 机构号 / 客户编号）. */
    private String subjectKey;

    /** 指标名称（中文）. */
    private String metricName;

    /** 指标数值. */
    private BigDecimal value;
}
