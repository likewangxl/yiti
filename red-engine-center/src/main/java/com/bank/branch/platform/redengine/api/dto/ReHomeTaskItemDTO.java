package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 组织角色首页的本组织任务条目。 */
@Data
@Schema(description = "红色引擎首页组织任务")
public class ReHomeTaskItemDTO {

    private Long taskId;
    private String taskNo;
    private String title;
    private String description;
    private String taskType;
    private String taskNature;
    private String cycle;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime currentWindowStartAt;
    private LocalDateTime currentWindowEndAt;
}
