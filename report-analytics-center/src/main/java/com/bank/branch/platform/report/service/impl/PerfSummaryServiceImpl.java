package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.report.dto.req.PerfSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.PerfSummaryRowVO;
import com.bank.branch.platform.report.service.ExportTaskService;
import com.bank.branch.platform.report.service.PerfSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link PerfSummaryService} 的 Red 占位实现（Task M3.2.1，Red 阶段未实装）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfSummaryServiceImpl implements PerfSummaryService {

    private final KpiApi kpiApi;

    private final CurrentUserApi currentUserApi;

    private final ExportTaskService exportTaskService;

    @Override
    public PageResult<PerfSummaryRowVO> getPerfSummary(PerfSummaryReqDTO req, PageRequest page) {
        throw new UnsupportedOperationException("Red 占位，Green 阶段实装");
    }

    @Override
    public ExportTaskRespDTO submitPerfSummaryExport(PerfSummaryReqDTO req) {
        throw new UnsupportedOperationException("Red 占位，Green 阶段实装");
    }
}
