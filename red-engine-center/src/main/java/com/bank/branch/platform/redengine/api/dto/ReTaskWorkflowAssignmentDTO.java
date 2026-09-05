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
    /** 当前任务提交版本对应的四维材料维度编码。 */
    private String dimensionCode;
    /** 当前任务提交版本对应的四维材料明细项编码。 */
    private String itemCode;
    /** 当前提交版本最近一次审核/驳回意见，稳定契约字段名为 reviewFeedback。 */
    private String reviewFeedback;
    /**
     * 当前 assignment 的审核处理历史。详情接口返回按发生时间升序排列的审核动作；
     * 分页列表为避免逐行加载历史，返回空集合。
     */
    private List<ReTaskWorkflowHistoryDTO> reviewHistory;
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
