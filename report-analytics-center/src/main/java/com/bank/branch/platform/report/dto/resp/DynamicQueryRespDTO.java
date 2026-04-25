package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 动态查询响应 DTO（A.2 POST /api/reports/dynamic-query）.
 *
 * <p>Rows 结构：每行 Map 固定 key {@code subjectId / subjectName}，
 *   其余 key 为 {@link MetricColumnDTO#getMetricCode() metricCode}，
 *   value 为 {@code BigDecimal} 指标值（未查到时省略该 key）.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DynamicQueryRespDTO {

    /** 维度：EMP / ORG / CUST */
    private String dim;

    /** 数据日期 */
    private LocalDate dataDate;

    /** 列定义 */
    private List<MetricColumnDTO> columns;

    /** 行数据：每行 Map 含 subjectId / subjectName + 各 metricCode → 值 */
    private List<Map<String, Object>> rows;

    /** 行数（= rows.size()） */
    private Integer rowCount;
}
