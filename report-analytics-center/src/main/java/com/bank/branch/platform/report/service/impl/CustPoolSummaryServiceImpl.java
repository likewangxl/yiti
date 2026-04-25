package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.report.dto.req.CustPoolSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.CustPoolSummaryVO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.service.CustPoolSummaryService;
import com.bank.branch.platform.report.service.ExportTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link CustPoolSummaryService} 的 Red 占位实现（Task M3.3.1，Red 阶段未实装）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustPoolSummaryServiceImpl implements CustPoolSummaryService {

    private final CustomerQueryApi customerQueryApi;

    private final CurrentUserApi currentUserApi;

    private final ExportTaskService exportTaskService;

    @Override
    public PageResult<CustPoolSummaryVO> getCustPoolSummary(CustPoolSummaryReqDTO req, PageRequest page) {
        throw new UnsupportedOperationException("Red 占位，Green 阶段实装");
    }

    @Override
    public ExportTaskRespDTO submitCustPoolSummaryExport(CustPoolSummaryReqDTO req) {
        throw new UnsupportedOperationException("Red 占位，Green 阶段实装");
    }
}
