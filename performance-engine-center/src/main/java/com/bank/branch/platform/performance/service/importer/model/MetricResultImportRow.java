package com.bank.branch.platform.performance.service.importer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * <p>{@code dataDate} 来源于所在 Sheet 名（约定 yyyy-MM-dd 或 yyyyMMdd），整 Sheet 共用。
 *
 * <p>用 POI 而非 EasyExcel 解析的原因：需要拿 Sheet 名作为 dataDate，整文件可能多 Sheet。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricResultImportRow {

    /** 所在 Sheet 名（同时承载数据日期）. */
    private String sheetName;

    /** 解析后的数据日期（按 Sheet 名 yyyy-MM-dd / yyyyMMdd / yyyy/M/d 尝试解析）. */
    private LocalDate dataDate;

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
