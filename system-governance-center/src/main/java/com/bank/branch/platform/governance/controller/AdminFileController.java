package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 全局文件列表
 * <p>
 * 区别于 {@link FileController}：FileController 是按业务关联查文件
 * （强制 bizType + bizId），本 controller 给"系统设置 → 文件管理"页面用，
 * 直接列 file_object 全表 + 模糊/精确过滤。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sys/files")
@Tag(name = "文件管理(管理后台)", description = "全局文件列表 - 仅 SYS_CONFIG 权限可见")
public class AdminFileController {

    private final FileService fileService;

    /**
     * 全局文件列表（分页）
     *
     * @param fileName   文件名关键字（模糊），可空
     * @param fileType   文件类型（精确），可空
     * @param uploadedBy 上传人工号（精确），可空
     * @param startTime  上传时间下界，可空，格式 "yyyy-MM-dd HH:mm:ss"
     * @param endTime    上传时间上界，可空
     * @param pageNo     页码（默认 1）
     * @param pageSize   每页条数（默认 20）
     */
    @GetMapping
    @Operation(summary = "全局文件分页列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<FileObjectDTO> listAllFiles(
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[AdminFileController.listAllFiles] fileName={}, fileType={}, uploadedBy={}, page={}/{}",
                fileName, fileType, uploadedBy, pageNo, pageSize);
        PageResult<FileObjectDTO> result = fileService.listAllFiles(
                fileName, fileType, uploadedBy, startTime, endTime, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
