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
    /** 面向页面展示的动作标签，例如“支部通过”“组织驳回”。 */
    private String actionLabel;
    /** 面向页面展示的审核阶段标签，例如“支部审核”“组织审核”。 */
    private String stageLabel;
    private String fromStatus;
    private String toStatus;
    private String opinion;
    private String operatorId;
    private String operatorName;
    private LocalDateTime occurredAt;
}
