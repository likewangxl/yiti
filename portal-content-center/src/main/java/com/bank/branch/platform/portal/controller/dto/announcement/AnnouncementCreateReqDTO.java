package com.bank.branch.platform.portal.controller.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告新增请求 DTO
 */
@Data
public class AnnouncementCreateReqDTO {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题最长200字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;
}
