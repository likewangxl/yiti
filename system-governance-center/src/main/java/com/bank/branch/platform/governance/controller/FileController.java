package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理控制器
 * 提供文件上传、下载URL获取、业务关联绑定和关联文件查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传下载与业务关联管理")
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件
     *
     * @param file       上传的文件
     * @param uploadedBy 上传人工号（实际应从 CurrentUserContext 获取）
     * @return 文件对象DTO
     */
    @PostMapping("/api/files/upload")
    @Operation(summary = "上传文件")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<FileObjectDTO> upload(
            @RequestParam(value = "file") MultipartFile file,
            @RequestParam(value = "uploadedBy") String uploadedBy) {
        log.info("[FileController.upload] fileName={}, uploadedBy={}", file.getOriginalFilename(), uploadedBy);
        FileObjectDTO dto = fileService.upload(file, uploadedBy);
        return ResponseWrapper.success(dto);
    }

    /**
     * 获取文件预签名下载URL
     *
     * @param fileId 文件对象ID（路径参数）
     * @return 预签名下载URL
     */
    @GetMapping("/api/files/{fileId}/download-url")
    @Operation(summary = "获取文件下载URL")
    public ResponseWrapper<String> getDownloadUrl(
            @PathVariable(value = "fileId") String fileId) {
        log.debug("[FileController.getDownloadUrl] fileId={}", fileId);
        String url = fileService.getDownloadUrl(fileId);
        return ResponseWrapper.success(url);
    }

    /**
     * 关联文件到业务对象
     *
     * @param bizType      业务类型
     * @param bizId        业务ID
     * @param fileObjectId 文件对象ID
     * @param fileRole     文件用途
     * @return 成功响应
     */
    @PostMapping("/api/files/bind")
    @Operation(summary = "关联文件到业务对象")
    public ResponseWrapper<Void> bindFile(
            @RequestParam(value = "bizType") String bizType,
            @RequestParam(value = "bizId") String bizId,
            @RequestParam(value = "fileObjectId") String fileObjectId,
            @RequestParam(value = "fileRole") String fileRole) {
        log.info("[FileController.bindFile] bizType={}, bizId={}, fileObjectId={}, fileRole={}",
                bizType, bizId, fileObjectId, fileRole);
        fileService.bindFile(bizType, bizId, fileObjectId, fileRole);
        return ResponseWrapper.success();
    }

    /**
     * 查询业务关联的文件列表
     *
     * @param bizType 业务类型（路径参数）
     * @param bizId   业务ID（路径参数）
     * @return 文件对象DTO列表
     */
    @GetMapping("/api/files/biz/{bizType}/{bizId}")
    @Operation(summary = "查询业务关联文件列表")
    public ResponseWrapper<List<FileObjectDTO>> listBizFiles(
            @PathVariable(value = "bizType") String bizType,
            @PathVariable(value = "bizId") String bizId) {
        log.debug("[FileController.listBizFiles] bizType={}, bizId={}", bizType, bizId);
        List<FileObjectDTO> files = fileService.listBizFiles(bizType, bizId);
        return ResponseWrapper.success(files);
    }
}
