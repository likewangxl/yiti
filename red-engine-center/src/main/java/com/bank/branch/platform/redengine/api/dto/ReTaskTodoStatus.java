package com.bank.branch.platform.redengine.api.dto;

/** 员工任务待办状态。 */
public enum ReTaskTodoStatus {

    /** 待处理。 */
    PENDING,
    /** 随任务提交完成。 */
    COMPLETED,
    /** 任务被取消。 */
    CANCELLED
}
