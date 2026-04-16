package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 员工指标快照 DTO (供 report-analytics-center 批量查询).
 * <p>V1.0 仅定义结构, V1.1 由 MetricQueryApi.batchQueryEmpSnapshots 填充数据.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpMetricSnapshotDTO {
    private String empId;
    private LocalDate dataDate;
    private String version;
    /** 指标编码 → 指标值. */
    private Map<String, BigDecimal> metricValues;
}
