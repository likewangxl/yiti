package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;  // NOSONAR page() util
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.ReportTouchOrgVO;
import com.bank.branch.platform.report.service.TouchSummaryService;
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
 * 触达任务监控报表 REST 控制器（C 章 C.2，Task M3.1.1 + M3.1.2）.
 *
 * <p>M3.1.1：GET /api/reports/touch-task-summary 分页汇总
 * <p>M3.1.2：POST /api/reports/touch-task-summary/export 异步导出占位（M5 Worker 启用）
 *
 * <p>鉴权：@BizAuth(REPORT, READ) / @BizAuth(REPORT, EXPORT) + PT_RESOURCE（M3.4 注册）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-触达汇总", description = "C.2 触达任务监控 view + export")
@Validated
@RequiredArgsConstructor
public class TouchSummaryController {

    private final TouchSummaryService touchSummaryService;

    /**
     * C.2 GET /touch-task-summary 机构触达汇总（分页）.
     *
     * <p>返回类型 ResponseWrapper&lt;ReportTouchOrgVO&gt; 为 common-web 契约设计：
     * ResponseWrapper 同时持有 data（单对象）与 page（分页信息）两个字段，
     * 分页场景下调用 {@code ResponseWrapper.page(PageResult)}，data 为 null，
     * PageResult 通过 page 字段承载（参见 V1.4 S4.3 契约说明）.
     */
    @GetMapping("/touch-task-summary")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "C.2 GET 机构触达汇总")
    public ResponseWrapper<ReportTouchOrgVO> getTouchSummary(
            @Valid @ModelAttribute TouchSummaryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<ReportTouchOrgVO> result = touchSummaryService.getOrgTouchSummary(req, page);
        return ResponseWrapper.page(result);
    }

    /**
     * C.2 POST /touch-task-summary/export 异步导出（占位，M5 Worker 启用）.
     */
    @PostMapping("/touch-task-summary/export")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXPORT)
    @Operation(summary = "C.2 POST 触达汇总异步导出（占位）")
    public ResponseWrapper<ExportTaskRespDTO> submitTouchSummaryExport(
            @Valid @RequestBody TouchSummaryReqDTO req) {
        return ResponseWrapper.success(touchSummaryService.submitTouchSummaryExport(req));
    }
}
