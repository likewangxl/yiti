package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中场支持申请审批驳回事件。
 * 在工作流完成且结果为驳回（outcome=REJECTED）时发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportRejectedEvent {

    /** 申请ID */
    private String requestId;

    /** 申请编号 */
    private String requestNo;

    /** 客户ID */
    private String custId;

    /** 发起人工号 */
    private String createdBy;

    /**
     * 驳回原因。
     * 来源：ProcessCompletedEvent.reason()，由工作流审批节点填写，允许为 null。
     */
    private String rejectReason;
}
