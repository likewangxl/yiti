package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementFileDTO;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementRespDTO;
import com.bank.branch.platform.portal.entity.AnnouncementFile;
import com.bank.branch.platform.portal.service.AnnouncementService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 公告管理控制器
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "公告管理", description = "公告发布与查询")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final FileApi fileApi;

    // ==================== 公开端点 ====================

    /** 分页查询公告列表 */
    @GetMapping("/api/portal/announcements")
    @Operation(summary = "公告列表（分页）")
    public ResponseWrapper<IPage<AnnouncementRespDTO>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(announcementService.listAnnouncements(pageNo, pageSize, keyword));
    }

    /** 最近 N 条公告（工作台卡片用） */
    @GetMapping("/api/portal/announcements/recent")
    @Operation(summary = "最近公告")
    public ResponseWrapper<List<AnnouncementRespDTO>> recent(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseWrapper.success(announcementService.listRecent(limit));
    }

    /** 公告详情（含文件列表） */
    @GetMapping("/api/portal/announcements/{id}")
    @Operation(summary = "公告详情")
    public ResponseWrapper<AnnouncementRespDTO> detail(@PathVariable String id) {
        return ResponseWrapper.success(announcementService.getDetail(id));
    }

    /** 下载公告附件 */
    @GetMapping("/api/portal/announcements/files/{fileId}/download")
    @Operation(summary = "下载公告附件")
    public void downloadFile(@PathVariable String fileId, HttpServletResponse response) throws IOException {
        AnnouncementFile af = announcementService.getFile(fileId);
        // filePath 列存的是 OBS fileId，从 OBS 读字节流式回传
        byte[] data = fileApi.getFileContent(af.getFilePath());
        String encoded = URLEncoder.encode(af.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(data.length);
        response.getOutputStream().write(data);
    }

    // ==================== 管理端点 ====================

    /** 管理端分页查询（含已删除） */
    @GetMapping("/api/admin/announcements")
    @Operation(summary = "管理端公告列表（含已删除）")
    public ResponseWrapper<IPage<AnnouncementRespDTO>> adminList(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(announcementService.listAnnouncementsAdmin(pageNo, pageSize, keyword));
    }

    /** 新增公告 */
    @PostMapping("/api/admin/announcements")
    @Operation(summary = "新增公告")
    public ResponseWrapper<String> create(@Valid @RequestBody AnnouncementCreateReqDTO req) {
        return ResponseWrapper.success(announcementService.createAnnouncement(req));
    }

    /** 上传公告附件 */
    @PostMapping("/api/admin/announcements/{announcementId}/files")
    @Operation(summary = "上传公告附件")
    public ResponseWrapper<AnnouncementFileDTO> uploadFile(
            @PathVariable String announcementId,
            @RequestParam("file") MultipartFile file) {
        return ResponseWrapper.success(announcementService.uploadFile(announcementId, file));
    }

    /** 置顶/取消置顶公告 */
    @PutMapping("/api/admin/announcements/{id}/toggle-pin")
    @Operation(summary = "置顶/取消置顶公告")
    public ResponseWrapper<Void> togglePin(@PathVariable String id) {
        announcementService.togglePin(id);
        return ResponseWrapper.success(null);
    }

    /** 逻辑删除公告 */
    @DeleteMapping("/api/admin/announcements/{id}")
    @Operation(summary = "删除公告")
    public ResponseWrapper<Void> delete(@PathVariable String id) {
        announcementService.deleteAnnouncement(id);
        return ResponseWrapper.success(null);
    }
}
