package com.bank.branch.platform.portal.controller.dto.announcement;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告附件响应 DTO
 */
@Data
public class AnnouncementFileDTO {

    private String id;

    private String fileName;

    private Long fileSize;

    private LocalDateTime uploadTime;
}
