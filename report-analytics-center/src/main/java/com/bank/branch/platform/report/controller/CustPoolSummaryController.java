package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.CustPoolSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.CustPoolSummaryVO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.service.CustPoolSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户池统计报表 REST 控制器（C 章 C.4，Task M3.3.1 + M3.3.2）.
 *
 * <p>M3.3.1：GET /api/reports/customer-pool-summary 客户池汇总（按等级分组）
 * <p>M3.3.2：POST /api/reports/customer-pool-summary/export 异步导出占位（M5 Worker 启用）
 *
 * <p>鉴权：@BizAuth(REPORT, READ) / @BizAuth(REPORT, EXPORT) + PT_RESOURCE（M3.4 注册）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-客户池汇总", description = "C.4 客户池统计 view + export")
@Validated
@RequiredArgsConstructor
public class CustPoolSummaryController {

    private final CustPoolSummaryService custPoolSummaryService;

    /**
     * C.4 GET /customer-pool-summary 客户池汇总（按等级分组，VIP/NORMAL/POTENTIAL）.
     */
    @GetMapping("/customer-pool-summary")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "C.4 GET 客户池统计")
    public ResponseWrapper<CustPoolSummaryVO> getCustPoolSummary(
            @Valid @ModelAttribute CustPoolSummaryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<CustPoolSummaryVO> result = custPoolSummaryService.getCustPoolSummary(req, page);
        return ResponseWrapper.page(result);
    }

    /**
     * C.4 POST /customer-pool-summary/export 异步导出（占位，M5 Worker 启用）.
     */
    @PostMapping("/customer-pool-summary/export")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXPORT)
    @Operation(summary = "C.4 POST 客户池汇总异步导出（占位）")
    public ResponseWrapper<ExportTaskRespDTO> submitCustPoolSummaryExport(
            @Valid @RequestBody CustPoolSummaryReqDTO req) {
        return ResponseWrapper.success(custPoolSummaryService.submitCustPoolSummaryExport(req));
    }
}
