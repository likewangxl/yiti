package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询维度+指标树响应 DTO（A.1 GET /api/reports/query-dimensions）.
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code dim}：维度编码 EMP / ORG / CUST</li>
 *   <li>{@code dimName}：维度中文名（由 {@code REPORT_DIM} 字典翻译）</li>
 *   <li>{@code metrics}：按 category 分组后的两级指标树（groupCode → metricCode 列表）</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryDimensionRespDTO {

    /** 维度：EMP / ORG / CUST */
    private String dim;

    /** 维度中文名 */
    private String dimName;

    /** 分组后的指标树（分组节点 → 指标叶子节点） */
    private List<MetricTreeNodeDTO> metrics;
}
