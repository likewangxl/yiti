package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.service.DynamicQueryService;
import com.bank.branch.platform.report.service.ExportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 动态查询导出 Controller。
 *
 * <p>两种方式：</p>
 * <ul>
 *   <li>{@code POST /dynamic-query/export}      —— 异步任务占位（M1.3，返回 taskId，暂未实现 Worker）；</li>
 *   <li>{@code POST /dynamic-query/export-file} —— 同步直推 xlsx 流（前端点导出即下载，不依赖 MinIO）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-动态查询导出")
@Validated
@RequiredArgsConstructor
public class DynamicQueryExportController {

    private final ExportTaskService exportTaskService;
    private final DynamicQueryService dynamicQueryService;

    @PostMapping("/dynamic-query/export")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXPORT)
    @Operation(summary = "A.3 提交动态查询异步导出任务（占位，M5 启用 Worker）")
    public ResponseWrapper<ExportTaskRespDTO> submitExport(@Valid @RequestBody DynamicQueryReqDTO req) {
        return ResponseWrapper.success(exportTaskService.submitDynamicQueryExport(req));
    }

    /**
     * 同步导出：按当前查询条件直接生成 xlsx 并以附件流返回，前端点"导出"立即下载。
     */
    @PostMapping("/dynamic-query/export-file")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "同步导出动态查询结果为 Excel（直接返回文件流）")
    public void exportFile(@Valid @RequestBody DynamicQueryReqDTO req,
                           HttpServletResponse resp) throws IOException {
        byte[] bytes = dynamicQueryService.exportExcel(req);
        String fileName = "dynamic_query_" + req.getDim() + ".xlsx";
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        resp.getOutputStream().write(bytes);
        resp.flushBuffer();
    }
}
