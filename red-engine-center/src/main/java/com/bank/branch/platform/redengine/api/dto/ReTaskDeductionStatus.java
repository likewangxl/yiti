package com.bank.branch.platform.redengine.api.dto;

/** 任务逾期扣分状态。 */
public enum ReTaskDeductionStatus {

    /** 待组织管理员执行。 */
    PENDING,
    /** 已执行。 */
    EXECUTED,
    /** 已取消。 */
    CANCELLED
}
