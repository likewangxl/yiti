package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.PerfSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.PerfSummaryRowVO;
import com.bank.branch.platform.report.service.PerfSummaryService;
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
 * 绩效汇总报表 REST 控制器（C 章 C.3，Task M3.2.1 + M3.2.2）.
 *
 * <p>M3.2.1：GET /api/reports/perf-summary 分页绩效汇总（KpiApi 单条循环）
 * <p>M3.2.2：POST /api/reports/perf-summary/export 异步导出占位（M5 Worker 启用）
 *
 * <p>鉴权：@BizAuth(REPORT, READ) / @BizAuth(REPORT, EXPORT) + PT_RESOURCE（M3.4 注册）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-绩效汇总", description = "C.3 绩效汇总 view + export")
@Validated
@RequiredArgsConstructor
public class PerfSummaryController {

    private final PerfSummaryService perfSummaryService;

    /**
     * C.3 GET /perf-summary 绩效汇总（分页）.
     */
    @GetMapping("/perf-summary")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "C.3 GET 绩效汇总")
    public ResponseWrapper<PerfSummaryRowVO> getPerfSummary(
            @Valid @ModelAttribute PerfSummaryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<PerfSummaryRowVO> result = perfSummaryService.getPerfSummary(req, page);
        return ResponseWrapper.page(result);
    }

    /**
     * C.3 POST /perf-summary/export 异步导出（占位，M5 Worker 启用）.
     */
    @PostMapping("/perf-summary/export")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXPORT)
    @Operation(summary = "C.3 POST 绩效汇总异步导出（占位）")
    public ResponseWrapper<ExportTaskRespDTO> submitPerfSummaryExport(
            @Valid @RequestBody PerfSummaryReqDTO req) {
        return ResponseWrapper.success(perfSummaryService.submitPerfSummaryExport(req));
    }
}
