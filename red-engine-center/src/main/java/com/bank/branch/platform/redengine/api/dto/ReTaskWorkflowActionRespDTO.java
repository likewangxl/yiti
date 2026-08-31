package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 任务审核动作结果。 */
@Data
@Schema(description = "任务审核动作结果")
public class ReTaskWorkflowActionRespDTO {

    private Long assignmentId;
    private Long submissionId;
    private ReTaskAssignmentStatus assignmentStatus;
    private ReTaskSubmissionStatus submissionStatus;
    private boolean idempotent;
}
