package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批日志DTO。
 * <p>
 * 记录流程审批过程中每个节点的操作信息，
 * 包括操作人、操作类型、审批意见和操作时间。
 * </p>
 */
@Data
public class ApprovalLogDTO {

    /** 操作人工号 */
    private String userId;

    /** 操作类型（如 APPROVE / REJECT / TRANSFER） */
    private String action;

    /** 审批意见 */
    private String comment;

    /** 操作时间 */
    private LocalDateTime time;
}
