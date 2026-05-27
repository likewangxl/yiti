package com.bank.branch.platform.portal.controller.dto.announcement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告响应 DTO
 */
@Data
public class AnnouncementRespDTO {

    private String id;

    private String title;

    private String content;

    private String publisherId;

    private String publisherName;

    private LocalDateTime publishDate;

    private Boolean isPinned;

    private String status;

    private List<AnnouncementFileDTO> files;
}
