package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
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
 *
 * <p>鉴权（2026-07-19 修复）：全部 9 个端点补标 {@code @BizAuth}，
 * {@code bizType} 统一复用 {@link BizType#SYS_CONFIG}（不新增独立 BizType）——
 * 公告管理菜单挂在「系统设置」分组下（PT_RESOURCE 的 {@code M_SYS_ANN} 挂
 * {@code M_GROUP_SYSTEM}），与 auth/governance/workflow 模块里同样归为
 * 系统级杂项管理的端点（角色/字典/日历/任务调度等）一致复用 SYS_CONFIG，
 * 不违反"扩展新 BizType 前优先复用现有枚举"的红线。
 * 修复前 9 个端点完全没有标注 {@code @BizAuth}——对应的 {@code RES_ANN_*}
 * 资源早已在 {@code PT_RESOURCE} 完整登记，RBAC（Step1 资源匹配 + Step2
 * 角色-资源绑定）本身不受影响，但 {@code AuthorizationInterceptor} Step3
 * （{@code BizMetaResolver}）因缺注解而直接放行并退化为最小
 * {@code DataScopeContext}，不解析 bizType/action。见
 * {@code docs/modules/portal-content-center/03-接口设计与报文.md} §J 说明。</p>
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
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.LIST)
    @Operation(summary = "公告列表（分页）")
    public ResponseWrapper<IPage<AnnouncementRespDTO>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(announcementService.listAnnouncements(pageNo, pageSize, keyword));
    }

    /** 最近 N 条公告（工作台卡片用） */
    @GetMapping("/api/portal/announcements/recent")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.LIST)
    @Operation(summary = "最近公告")
    public ResponseWrapper<List<AnnouncementRespDTO>> recent(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseWrapper.success(announcementService.listRecent(limit));
    }

    /** 公告详情（含文件列表） */
    @GetMapping("/api/portal/announcements/{id}")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    @Operation(summary = "公告详情")
    public ResponseWrapper<AnnouncementRespDTO> detail(@PathVariable String id) {
        return ResponseWrapper.success(announcementService.getDetail(id));
    }

    /** 下载公告附件 */
    @GetMapping("/api/portal/announcements/files/{fileId}/download")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
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
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.LIST)
    @Operation(summary = "管理端公告列表（含已删除）")
    public ResponseWrapper<IPage<AnnouncementRespDTO>> adminList(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ResponseWrapper.success(announcementService.listAnnouncementsAdmin(pageNo, pageSize, keyword));
    }

    /** 新增公告 */
    @PostMapping("/api/admin/announcements")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.WRITE)
    @Operation(summary = "新增公告")
    public ResponseWrapper<String> create(@Valid @RequestBody AnnouncementCreateReqDTO req) {
        return ResponseWrapper.success(announcementService.createAnnouncement(req));
    }

    /** 上传公告附件 */
    @PostMapping("/api/admin/announcements/{announcementId}/files")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.WRITE)
    @Operation(summary = "上传公告附件")
    public ResponseWrapper<AnnouncementFileDTO> uploadFile(
            @PathVariable String announcementId,
            @RequestParam("file") MultipartFile file) {
        return ResponseWrapper.success(announcementService.uploadFile(announcementId, file));
    }

    /** 置顶/取消置顶公告 */
    @PutMapping("/api/admin/announcements/{id}/toggle-pin")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.WRITE)
    @Operation(summary = "置顶/取消置顶公告")
    public ResponseWrapper<Void> togglePin(@PathVariable String id) {
        announcementService.togglePin(id);
        return ResponseWrapper.success(null);
    }

    /** 逻辑删除公告 */
    @DeleteMapping("/api/admin/announcements/{id}")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.DELETE)
    @Operation(summary = "删除公告")
    public ResponseWrapper<Void> delete(@PathVariable String id) {
        announcementService.deleteAnnouncement(id);
        return ResponseWrapper.success(null);
    }
}
