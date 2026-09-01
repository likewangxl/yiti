package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 任务审核动作结果。 */
@Data
@Schema(description = "任务审核动作结果")
public class ReTaskWorkflowActionRespDTO {

    private Long assignmentId;
    private Long submissionId;
    /** 四维旧材料审核记录 ID，供原评分入口继续使用。 */
    private Long legacyReviewId;
    /** 四维旧材料上报 ID 的兼容别名。 */
    private Long submitId;
    private ReTaskAssignmentStatus assignmentStatus;
    private ReTaskSubmissionStatus submissionStatus;
    private boolean idempotent;
}
