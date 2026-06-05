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

    /** 节点ID */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 操作人 USER_ID（PT_USER.user_id 内部主键） */
    private String operator;

    /** 操作人工号（PT_USER.username，展示用） */
    private String operatorEmpNo;

    /** 操作人姓名（中文姓名 PT_USER.userchnname） */
    private String operatorName;

    /** 操作人机构名称 */
    private String operatorOrgName;

    /** 操作类型：SUBMIT / APPROVE / REJECT / CLAIM / TRANSFER */
    private String action;

    /** 审批意见 */
    private String opinion;

    /** 操作时间 */
    private LocalDateTime operateTime;
}
