package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementFileDTO;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementRespDTO;
import com.bank.branch.platform.portal.entity.Announcement;
import com.bank.branch.platform.portal.entity.AnnouncementFile;
import com.bank.branch.platform.portal.mapper.AnnouncementFileMapper;
import com.bank.branch.platform.portal.mapper.AnnouncementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 公告管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementFileMapper announcementFileMapper;
    private final CurrentUserApi currentUserApi;

    @Value("${announcement.upload-dir:/home/djdev/lf/yiti/announcement-files}")
    private String uploadDir;

    /**
     * 分页查询公告列表（仅未删除，公开端用）
     */
    public IPage<AnnouncementRespDTO> listAnnouncements(int pageNo, int pageSize, String keyword) {
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getIsDeleted, 0)
                .like(StringUtils.hasText(keyword), Announcement::getTitle, keyword)
                .orderByDesc(Announcement::getIsPinned)
                .orderByDesc(Announcement::getPublishDate);
        IPage<Announcement> page = announcementMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return page.convert(this::toRespDto);
    }

    /**
     * 分页查询公告列表（含已删除，管理端用）
     */
    public IPage<AnnouncementRespDTO> listAnnouncementsAdmin(int pageNo, int pageSize, String keyword) {
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<Announcement>()
                .like(StringUtils.hasText(keyword), Announcement::getTitle, keyword)
                .orderByDesc(Announcement::getIsPinned)
                .orderByDesc(Announcement::getPublishDate);
        IPage<Announcement> page = announcementMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return page.convert(this::toRespDto);
    }

    /**
     * 查询最近 N 条公告（工作台用）
     */
    public List<AnnouncementRespDTO> listRecent(int limit) {
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getIsDeleted, 0)
                .orderByDesc(Announcement::getIsPinned)
                .orderByDesc(Announcement::getPublishDate)
                .last("LIMIT " + limit);
        List<Announcement> list = announcementMapper.selectList(qw);
        return list.stream().map(this::toRespDto).toList();
    }

    /**
     * 查询公告详情（含关联文件）
     */
    public AnnouncementRespDTO getDetail(String id) {
        Announcement entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new BizException("PORTAL-40401", "公告不存在");
        }
        AnnouncementRespDTO dto = toRespDto(entity);
        List<AnnouncementFile> files = announcementFileMapper.selectList(
                new LambdaQueryWrapper<AnnouncementFile>().eq(AnnouncementFile::getAnnouncementId, id));
        dto.setFiles(files.stream().map(this::toFileDto).toList());
        return dto;
    }

    /**
     * 新增公告
     */
    @Transactional
    public String createAnnouncement(AnnouncementCreateReqDTO req) {
        CurrentUserContext ctx = currentUserApi.getCurrentUserContext();
        String empId = ctx.empId();
        String empName = ctx.displayName();

        Announcement entity = new Announcement();
        entity.setId(UUID.randomUUID().toString());
        entity.setTitle(req.getTitle());
        entity.setContent(req.getContent());
        entity.setPublisherId(empId);
        entity.setPublisherName(empName);
        entity.setPublishDate(LocalDateTime.now());
        entity.setIsDeleted(0);
        entity.setCreatedBy(empId);
        entity.setCreatedTime(LocalDateTime.now());
        announcementMapper.insert(entity);

        log.info("[AnnouncementService.create] id={}, title={}, publisherId={}", entity.getId(), entity.getTitle(), empId);
        return entity.getId();
    }

    /**
     * 上传公告附件（保存到本地文件系统）
     */
    public AnnouncementFileDTO uploadFile(String announcementId, MultipartFile file) {
        String empId = currentUserApi.getCurrentEmpId();
        String fileId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = fileId + ext;

        try {
            Path dir = Paths.get(uploadDir, announcementId);
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            file.transferTo(target.toFile());

            AnnouncementFile af = new AnnouncementFile();
            af.setId(fileId);
            af.setAnnouncementId(announcementId);
            af.setFileName(originalName);
            af.setFileSize(file.getSize());
            af.setFilePath(target.toString());
            af.setUploadedBy(empId);
            af.setUploadTime(LocalDateTime.now());
            announcementFileMapper.insert(af);

            log.info("[AnnouncementService.uploadFile] fileId={}, annId={}, fileName={}", fileId, announcementId, originalName);
            return toFileDto(af);
        } catch (IOException e) {
            throw new BizException("PORTAL-50001", "文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件存储路径（用于下载）
     */
    public AnnouncementFile getFile(String fileId) {
        AnnouncementFile af = announcementFileMapper.selectById(fileId);
        if (af == null) {
            throw new BizException("PORTAL-40401", "文件不存在");
        }
        return af;
    }

    /**
     * 逻辑删除公告
     */
    @Transactional
    public void deleteAnnouncement(String id) {
        Announcement entity = announcementMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new BizException("PORTAL-40401", "公告不存在");
        }
        String empId = currentUserApi.getCurrentEmpId();
        entity.setIsDeleted(1);
        entity.setUpdatedBy(empId);
        entity.setUpdatedTime(LocalDateTime.now());
        announcementMapper.updateById(entity);
        log.info("[AnnouncementService.delete] id={}, operator={}", id, empId);
    }

    /**
     * 置顶/取消置顶公告
     */
    @Transactional
    public void togglePin(String id) {
        Announcement entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new BizException("PORTAL-40401", "公告不存在");
        }
        entity.setIsPinned(entity.getIsPinned() != null && entity.getIsPinned() == 1 ? 0 : 1);
        entity.setUpdatedBy(currentUserApi.getCurrentEmpId());
        entity.setUpdatedTime(LocalDateTime.now());
        announcementMapper.updateById(entity);
        log.info("[AnnouncementService.togglePin] id={}, isPinned={}", id, entity.getIsPinned());
    }

    private AnnouncementRespDTO toRespDto(Announcement entity) {
        AnnouncementRespDTO dto = new AnnouncementRespDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setPublisherId(entity.getPublisherId());
        dto.setPublisherName(entity.getPublisherName());
        dto.setPublishDate(entity.getPublishDate());
        dto.setIsPinned(entity.getIsPinned() != null && entity.getIsPinned() == 1);
        dto.setStatus(entity.getIsDeleted() == 1 ? "已删除" : "已发布");
        return dto;
    }

    private AnnouncementFileDTO toFileDto(AnnouncementFile af) {
        AnnouncementFileDTO dto = new AnnouncementFileDTO();
        dto.setId(af.getId());
        dto.setFileName(af.getFileName());
        dto.setFileSize(af.getFileSize());
        dto.setUploadTime(af.getUploadTime());
        return dto;
    }
}
