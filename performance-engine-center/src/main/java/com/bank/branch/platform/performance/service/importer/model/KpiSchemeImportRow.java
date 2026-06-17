package com.bank.branch.platform.performance.service.importer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * KPI 方案导入行模型（2026-06-17，importType=KPI_SCHEME）.
 *
 * <p>对齐「KPI方案上传模板.xlsx」（sheet1，第 0 行表头，从第 1 行起数据），10 列
 * （2026-06-17 取消「维度」列，方案项 base_dim 取自指标定义）：
 * <ol>
 *   <li>序号（透传，不参与业务）</li>
 *   <li>方案编号（必填）</li>
 *   <li>方案名称（必填）</li>
 *   <li>员工角色范围（方案级，按英文逗号分割的角色名称，可空；按方案分组取首次出现行的值）</li>
 *   <li>指标名称（必须存在于 PERF_METRIC_DEF.metric_name）</li>
 *   <li>表达式类型（"计算表达式"=FORMULA / "SQL表达式"=SQL，兼容大小写 FORMULA/SQL）</li>
 *   <li>表达式（可空；按类型路由 formula / sql_expr）</li>
 *   <li>权重（可空，可解析 BigDecimal）</li>
 *   <li>计分上线（对应 max_score，可空）</li>
 *   <li>计分下限（对应 min_score，可空）</li>
 * </ol>
 *
 * <p>与目标方案导入并行的新通道，整批 all-or-none 语义（任一行任一错误整批失败，不写任何库）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiSchemeImportRow {

    /** Excel 物理行号（仅用于错误消息，1-based，第一行表头）. */
    private int excelRowNum;

    /** 序号（透传，不参与业务）. */
    private String indexNo;

    /** 方案编号（必填）. */
    private String schemeCode;

    /** 方案名称（必填）. */
    private String schemeName;

    /** 员工角色范围原文（方案级，按英文逗号分割的角色名称，可空）. */
    private String empRoleScopeRaw;

    /** 指标名称（中文，必须存在于 PERF_METRIC_DEF）. */
    private String metricName;

    /** 表达式类型原文（"计算表达式" / "SQL表达式"，兼容 FORMULA/SQL）. */
    private String exprTypeRaw;

    /** 表达式内容（可空）. */
    private String exprContent;

    /** 权重（可空，由 writer 兜底默认）. */
    private BigDecimal weight;

    /** 计分上线（对应 max_score，可空）. */
    private BigDecimal maxScore;

    /** 计分下限（对应 min_score，可空）. */
    private BigDecimal minScore;
}
