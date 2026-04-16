package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表模块专用的指标批量查询 API (跨日期范围 / 矩阵查询).
 *
 * <p>V1.0 全部方法抛 UnsupportedOperationException; V1.1 交付真实实现.
 *
 * <p>消费方: report-analytics-center 专用.
 *
 * <p>约束:
 * <ul>
 *   <li>只读接口, 绕过缓存直连数据库 (从库)</li>
 *   <li>单次调用行数上限 100000, 超过抛 PERF-40002</li>
 * </ul>
 */
public interface MetricQueryApi {

    /**
     * 批量查询员工指标快照 (支持多员工 × 多日期 × 多指标矩阵).
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(List<String> empIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);

    /**
     * 批量查询机构指标快照.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);

    /**
     * 批量查询客户指标快照.
     * <p>V1.0 抛 UnsupportedOperationException; V1.1 实现.
     */
    List<CustMetricSnapshotDTO> batchQueryCustSnapshots(List<String> custIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);
}
