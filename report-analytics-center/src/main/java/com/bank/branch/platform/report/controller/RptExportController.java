package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.facade.RptExportFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表异步导出任务 REST 控制器（E 章 E.1/E.2/E.3，Task M5.3.1/2/3）.
 *
 * <p>3 端点：
 * <ul>
 *   <li>E.1 GET    /api/reports/export-tasks/{taskId}            导出任务状态查询</li>
 *   <li>E.2 DELETE /api/reports/export-tasks/{taskId}            取消（仅 PENDING/RUNNING）</li>
 *   <li>E.3 GET    /api/reports/export-tasks/{taskId}/download   下载（302 跳 MinIO 预签名 URL）</li>
 * </ul>
 *
 * <p>鉴权：@BizAuth(REPORT, READ/UPDATE/EXPORT) + PT_RESOURCE（M5.4.2 V1_0_6 注册）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/export-tasks")
@Tag(name = "报表-导出任务", description = "E 章异步导出任务状态/取消/下载")
@RequiredArgsConstructor
public class RptExportController {

    private final RptExportFacade facade;

    @GetMapping("/{taskId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "E.1 导出任务状态查询")
    public ResponseWrapper<ExportTaskRespDTO> getStatus(@PathVariable String taskId) {
        return ResponseWrapper.success(facade.getStatus(taskId));
    }

    @DeleteMapping("/{taskId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "E.2 取消导出任务（仅 PENDING/RUNNING）")
    public ResponseWrapper<Void> cancel(@PathVariable String taskId) {
        facade.cancel(taskId);
        return ResponseWrapper.success();
    }

    @GetMapping("/{taskId}/download")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXPORT)
    @Operation(summary = "E.3 下载导出文件（302 跳 MinIO 预签名 URL）")
    public ResponseEntity<Void> download(@PathVariable String taskId) {
        String url = facade.getDownloadUrl(taskId);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, url)
            .build();
    }
}
