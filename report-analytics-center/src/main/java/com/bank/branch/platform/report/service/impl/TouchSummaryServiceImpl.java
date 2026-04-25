package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.ReportTouchOrgVO;
import com.bank.branch.platform.report.service.ExportTaskService;
import com.bank.branch.platform.report.service.TouchSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TouchSummaryService 的 Red 占位实现（Task M3.1.1，Red 阶段未实装）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchSummaryServiceImpl implements TouchSummaryService {

    private final TouchTaskQueryApi touchTaskQueryApi;

    private final CurrentUserApi currentUserApi;

    private final OrgApi orgApi;

    private final ExportTaskService exportTaskService;

    @Override
    public PageResult<ReportTouchOrgVO> getOrgTouchSummary(TouchSummaryReqDTO req, PageRequest page) {
        throw new UnsupportedOperationException("Red 占位，Green 阶段实装");
    }

    @Override
    public ExportTaskRespDTO submitTouchSummaryExport(TouchSummaryReqDTO req) {
        throw new UnsupportedOperationException("Red 占位，Green 阶段实装");
    }
}
