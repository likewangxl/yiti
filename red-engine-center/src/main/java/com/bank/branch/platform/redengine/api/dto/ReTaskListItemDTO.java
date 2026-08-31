package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 任务管理列表行 DTO，不直接向 REST 暴露任务实体。 */
@Data
@Schema(description = "任务管理列表项")
public class ReTaskListItemDTO {

    private Long taskId;
    private String taskNo;
    private String title;
    private String description;
    private ReTaskNature taskNature;
    private ReTaskBusinessType businessType;
    private ReTaskCycleType cycleType;
    private Integer durationDays;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean requiresFile;
    private List<String> fileTypeCodes;
    private List<String> itemCodes;
    private ReTaskTargetType audienceType;
    private ReTaskStatus status;
    private LocalDateTime publishedAt;
    private long targetCount;
    private long submittedCount;
    private long approvedCount;
    private long rejectedCount;
    private long unreportedCount;
}
