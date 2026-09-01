package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务工作台 assignment 展示 DTO。
 *
 * <p>不直接暴露任务实体、提交实体或组织实体，避免前端依赖数据库模型；
 * taskTitle/taskDescription 等别名是工作台页面的稳定契约。</p>
 */
@Data
@Schema(description = "任务工作台分配详情")
public class ReTaskWorkflowAssignmentDTO {

    private Long taskId;
    private String taskNo;
    private String taskTitle;
    private String taskDescription;
    private ReTaskNature taskNature;
    private ReTaskBusinessType businessType;
    private ReTaskCycleType cycleType;
    private Integer durationDays;

    private Long taskInstanceId;
    private Long assignmentId;
    private Long branchId;
    private String branchName;
    private ReTaskAssignmentStatus status;
    private ReTaskSubmissionStatus submissionStatus;
    private Boolean isUnreported;
    private Boolean branchApproved;

    private String submitterId;
    private String submitterName;
    private LocalDateTime submittedAt;
    private String content;
    private String formData;
    private List<ReTaskAttachmentDTO> files;

    /** 四维旧材料审核记录 ID，供原评分入口继续使用。 */
    private Long legacyReviewId;
    /** 四维旧材料上报 ID 的兼容别名。 */
    private Long submitId;

    private LocalDateTime windowStartAt;
    private LocalDateTime windowEndAt;
    private Boolean requiresFile;
    private List<String> allowedFileTypes;
}
