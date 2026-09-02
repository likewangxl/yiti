package com.bank.branch.platform.redengine.api.dto;

/** 任务异步导出状态。 */
public enum ReTaskExportStatus {

    /** 等待执行。 */
    PENDING,
    /** 正在生成。 */
    RUNNING,
    /** 生成成功。 */
    SUCCESS,
    /** 生成失败。 */
    FAILED,
    /** 已取消。 */
    CANCELLED,
    /** 下载已过期。 */
    EXPIRED
}
