package com.bank.branch.platform.redengine.api.dto;

/** 任务周期实例状态。 */
public enum ReTaskInstanceStatus {

    /** 等待进入或正在等待调度。 */
    PENDING,
    /** 当前窗口开放。 */
    OPEN,
    /** 当前窗口已关闭。 */
    CLOSED,
    /** 已取消。 */
    CANCELLED
}
