package com.bank.branch.platform.redengine.api.dto;

/** 四大维度任务上传进度状态。 */
public enum ReTaskDimensionProgressStatus {

    /** 当前维度尚未上传。 */
    PENDING,
    /** 当前周期已有一次有效上传。 */
    COMPLETED
}
