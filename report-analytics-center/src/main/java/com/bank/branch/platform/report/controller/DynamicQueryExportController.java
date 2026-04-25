package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.service.ExportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态查询异步导出占位 Controller（A.3 POST /api/reports/dynamic-query/export，
 * Task M1.3.1 占位，M5 接入完整 Worker 链路）.
 *
 * <p>V1.0 M1.3 行为：仅创建 PENDING 任务并返回 taskId，
 * 不真正执行导出。M5 完成 Worker 后同接口返回值不变，但底层会走完整 EasyExcel + MinIO 流程。
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-动态查询导出", description = "异步导出（M1 占位，M5 启用）")
@Validated
@RequiredArgsConstructor
public class DynamicQueryExportController {

    private final ExportTaskService exportTaskService;

    @PostMapping("/dynamic-query/export")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXPORT)
    @Operation(summary = "A.3 提交动态查询异步导出任务（占位，M5 启用 Worker）")
    public ResponseWrapper<ExportTaskRespDTO> submitExport(@Valid @RequestBody DynamicQueryReqDTO req) {
        return ResponseWrapper.success(exportTaskService.submitDynamicQueryExport(req));
    }
}
