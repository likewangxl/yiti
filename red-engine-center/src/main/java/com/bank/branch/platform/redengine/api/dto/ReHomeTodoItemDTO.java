package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 首页待办/已办任务条目。 */
@Data
@Schema(description = "红色引擎首页任务条目")
public class ReHomeTodoItemDTO {

    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    private String title;
    private String description;
    private String taskType;
    private String taskNature;
    private String cycle;
    private String status;
    private LocalDateTime windowStartAt;
    private LocalDateTime windowEndAt;
    private LocalDateTime completedAt;
}
