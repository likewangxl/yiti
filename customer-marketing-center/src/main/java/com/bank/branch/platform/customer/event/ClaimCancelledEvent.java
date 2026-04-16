package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户认领取消事件
 * 取消认领后发布
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimCancelledEvent {

    /** 认领记录ID */
    private String claimId;

    /** 客户ID */
    private String custId;

    /** 认领机构代码 */
    private String orgId;

    /** 取消原因 */
    private String cancelReason;

    /** 操作人 */
    private String operatorEmpId;
}
