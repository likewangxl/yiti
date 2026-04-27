package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线索审批驳回事件。
 * <p>
 * 由 {@link com.bank.branch.platform.customer.listener.WorkflowCallbackListener} 在 REJECTED outcome 路径下发布，
 * 与 {@code bizapp.LoanRejectedEvent} pattern 对齐保留扩展点。
 * <br>
 * <strong>当前 V1 暂无下游 listener 消费</strong>，预留供后续业务（如驳回通知、驳回审计、驳回联动撤销等）订阅。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeadRejectedEvent {

    /** 线索ID */
    private String leadId;

    /** 线索编号 */
    private String leadNo;

    /** 操作类型: CREATE / UPDATE / DELETE */
    private String leadOp;

    /** 源客户ID（UPDATE/DELETE 时有值） */
    private String sourceCustId;

    /** 归属机构代码 */
    private String ownerOrgId;

    /** 驳回原因，可为 null（来自 ProcessCompletedEvent.reason） */
    private String rejectReason;

    /** 操作人 */
    private String operatorEmpId;
}
