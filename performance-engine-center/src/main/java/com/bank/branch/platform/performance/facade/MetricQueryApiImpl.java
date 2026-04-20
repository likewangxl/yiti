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
 */
@Service
public class MetricQueryApiImpl implements MetricQueryApi {

    @Override
    public List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(List<String> empIds,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public List<CustMetricSnapshotDTO> batchQueryCustSnapshots(List<String> custIds,
                                                               LocalDate dateFrom,
                                                               LocalDate dateTo,
                                                               List<String> metricCodes) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }
}
