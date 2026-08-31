package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskFileDownloadDTO;
import com.bank.branch.platform.redengine.service.ReTaskFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 任务附件查询和授权下载入口。 */
@Tag(name = "红色引擎-任务附件")
@RestController
@RequestMapping("/api/re/tasks")
@RequiredArgsConstructor
public class ReTaskFileController {

    private final ReTaskFileService fileService;
    private final CurrentUserApi currentUserApi;

    /** 查询 assignment 当前提交版本的附件列表。 */
    @Operation(summary = "任务附件列表")
    @GetMapping("/assignments/{assignmentId}/attachments")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<List<ReTaskAttachmentDTO>> listAttachments(@PathVariable Long assignmentId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(fileService.listAttachments(assignmentId, operatorId));
    }

    /**
     * 下载任务附件。
     *
     * <p>文件服务已先完成红色引擎归属校验，再从 governance FileApi 取得内容；
     * Controller 仅负责二进制响应头和内容包装。</p>
     */
    @Operation(summary = "下载任务附件")
    @GetMapping("/{taskId}/assignments/{assignmentId}/attachments/{fileId}/download")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseEntity<byte[]> download(@PathVariable Long taskId,
                                           @PathVariable Long assignmentId,
                                           @PathVariable String fileId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        ReTaskFileDownloadDTO file = fileService.download(taskId, assignmentId, fileId, operatorId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getFileName(), StandardCharsets.UTF_8).build();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.getContentType());
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.getContent());
    }
}
