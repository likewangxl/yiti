package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务状态历史展示 DTO。 */
@Data
@Schema(description = "任务状态历史")
public class ReTaskWorkflowHistoryDTO {

    private Long id;
    private Long submissionId;
    private String actionCode;
    private String fromStatus;
    private String toStatus;
    private String opinion;
    private String operatorId;
    private LocalDateTime occurredAt;
}
