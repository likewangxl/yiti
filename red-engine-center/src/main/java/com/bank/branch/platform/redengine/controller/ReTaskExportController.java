package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportRespDTO;
import com.bank.branch.platform.redengine.service.ReTaskExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 任务管理详情中的异步 ZIP 导出入口。 */
@Slf4j
@Tag(name = "红色引擎-任务导出")
@RestController
@RequestMapping("/api/re")
@RequiredArgsConstructor
public class ReTaskExportController {

    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final ReTaskExportService exportService;
    private final CurrentUserApi currentUserApi;

    /** 创建异步导出作业。 */
    @Operation(summary = "创建任务异步导出")
    @PostMapping("/tasks/{taskId}/exports")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.EXPORT)
    @AuditLog(action = "RE_TASK_EXPORT", resourceType = "RE_TASK")
    public ResponseWrapper<ReTaskExportRespDTO> create(
            @PathVariable Long taskId,
            @Valid @RequestBody(required = false) ReTaskExportReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        ReTaskExportRespDTO result = exportService.createExport(taskId, request, operatorId);
        log.info("[ReTaskExportController.create] taskId={}, exportId={}, operatorId={}",
                taskId, result.getExportId(), operatorId);
        return ResponseWrapper.success(result);
    }

    /** 查询异步导出状态。 */
    @Operation(summary = "查询任务导出状态")
    @GetMapping("/task-exports/{exportId}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReTaskExportRespDTO> status(@PathVariable String exportId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(exportService.getStatus(exportId, operatorId));
    }

    /** 下载已完成导出 ZIP；文件名固定由服务端生成，避免使用不可信路径。 */
    @Operation(summary = "下载任务导出 ZIP")
    @GetMapping("/task-exports/{exportId}/download")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.EXPORT)
    @AuditLog(action = "RE_TASK_EXPORT_DOWNLOAD", resourceType = "RE_TASK_EXPORT")
    public ResponseEntity<byte[]> download(@PathVariable String exportId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        byte[] bytes = exportService.download(exportId, operatorId);
        String filename = "red-engine-task-export-" + safeFilePart(exportId) + ".zip";
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(ZIP_CONTENT_TYPE))
                .body(bytes);
    }

    private String safeFilePart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
