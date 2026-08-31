package com.bank.branch.platform.redengine.api.dto;

/** 任务定义状态。 */
public enum ReTaskStatus {

    /** 尚未发布。 */
    DRAFT,
    /** 已发布。 */
    PUBLISHED,
    /** 已暂停。 */
    PAUSED,
    /** 已关闭。 */
    CLOSED,
    /** 已取消。 */
    CANCELLED
}
