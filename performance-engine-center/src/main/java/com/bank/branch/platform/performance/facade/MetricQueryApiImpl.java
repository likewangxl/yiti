package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.MetricQueryApi;
import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表专用指标批量查询 API 实现.
 *
 * <p>V1.1 未交付：3 个 {@code batchQuery*Snapshots} 方法均为 report-analytics-center
 * 报表专用批量快照查询，V1.1 计算/KPI/导入/上报/回算 scope 未覆盖，保留 UOE 占位至 V1.2。
 *
 * <p>V1.1 已交付的宽表批查请使用 {@code MetricApi.getEmpMetricValues / getOrgMetricValues /
 * getCustMetricValues}（单维度主键 + (metricCode -> value) 扁平映射，批量上限 100）。
 */
@Service
public class MetricQueryApiImpl implements MetricQueryApi {

    @Override
    public List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(List<String> empIds,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        // V1.2 交付：报表专用批量快照（多主键 × 日期区间 × 指标清单），V1.1 scope 未覆盖
        throw new UnsupportedOperationException("V1.2 delivered");
    }

    @Override
    public List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        // V1.2 交付：报表专用批量快照（多主键 × 日期区间 × 指标清单），V1.1 scope 未覆盖
        throw new UnsupportedOperationException("V1.2 delivered");
    }

    @Override
    public List<CustMetricSnapshotDTO> batchQueryCustSnapshots(List<String> custIds,
                                                               LocalDate dateFrom,
                                                               LocalDate dateTo,
                                                               List<String> metricCodes) {
        // V1.2 交付：报表专用批量快照（多主键 × 日期区间 × 指标清单），V1.1 scope 未覆盖
        throw new UnsupportedOperationException("V1.2 delivered");
    }
}
