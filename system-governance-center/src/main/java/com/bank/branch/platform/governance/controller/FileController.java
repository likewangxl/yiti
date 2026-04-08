package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件管理控制器
 * 提供文件上传、下载、业务关联绑定和关联文件查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
@Tag(name = "文件管理", description = "文件上传下载与业务关联管理")
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件（G.1）
     *
     * @param file       上传的文件
     * @param uploadedBy 上传人工号（实际应从 CurrentUserContext 获取）
     * @return 文件对象DTO
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public ResponseWrapper<FileObjectDTO> upload(
            @RequestParam(value = "file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy) {
        log.info("[FileController.upload] fileName={}, uploadedBy={}", file.getOriginalFilename(), uploadedBy);
        // 简化：如果未传 uploadedBy，使用默认值
        String uploader = uploadedBy != null ? uploadedBy : "ANONYMOUS";
        FileObjectDTO dto = fileService.upload(file, uploader);
        return ResponseWrapper.success(dto);
    }

    /**
     * 下载文件（G.2）- 302重定向到MinIO预签名URL
     *
     * @param fileId   文件对象ID
     * @param response HTTP响应，用于重定向
     */
    @GetMapping("/{fileId}/download")
    @Operation(summary = "下载文件")
    public void downloadFile(
            @PathVariable(value = "fileId") String fileId,
            HttpServletResponse response) throws IOException {
        log.info("[FileController.downloadFile] fileId={}", fileId);
        String presignedUrl = fileService.getDownloadUrl(fileId);
        // 302重定向到预签名URL
        response.sendRedirect(presignedUrl);
    }

    /**
     * 查询业务关联的文件列表（G.3）
     *
     * @param bizType 业务类型（Query参数）
     * @param bizId   业务ID（Query参数）
     * @return 文件对象DTO列表
     */
    @GetMapping
    @Operation(summary = "查询业务关联文件列表")
    public ResponseWrapper<List<FileObjectDTO>> listBizFiles(
            @RequestParam(value = "bizType") String bizType,
            @RequestParam(value = "bizId") String bizId) {
        log.debug("[FileController.listBizFiles] bizType={}, bizId={}", bizType, bizId);
        List<FileObjectDTO> files = fileService.listBizFiles(bizType, bizId);
        return ResponseWrapper.success(files);
    }

    /**
     * 删除文件（G.4）
     *
     * @param fileId 文件对象ID
     * @return 成功响应
     */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件")
    public ResponseWrapper<Void> deleteFile(@PathVariable(value = "fileId") String fileId) {
        log.info("[FileController.deleteFile] fileId={}", fileId);
        fileService.deleteFile(fileId);
        return ResponseWrapper.success();
    }
}
