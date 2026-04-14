package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中场支持申请完成事件。
 * 在申请状态变为 COMPLETED 或 REJECTED 时发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportCompletedEvent {

    /** 申请ID */
    private String requestId;

    /** 申请编号 */
    private String requestNo;

    /** 客户ID */
    private String custId;

    /** 产品ID */
    private String productId;

    /** 承接办理人工号 */
    private String assignedEmpId;

    /** true=成功完成，false=拒绝 */
    private boolean success;
}
