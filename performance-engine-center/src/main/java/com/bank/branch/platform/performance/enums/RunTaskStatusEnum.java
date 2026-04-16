package com.bank.branch.platform.performance.enums;

/**
 * 任务执行状态 (perf_run_task.status).
 */
public enum RunTaskStatusEnum {
    /** 待执行. */
    PENDING,
    /** 执行中. */
    RUNNING,
    /** 成功. */
    SUCCESS,
    /** 失败. */
    FAILED,
    /** 部分成功 (批量导入场景). */
    PARTIAL,
    /** 已取消. */
    CANCELLED;

    public static boolean isValid(String v) {
        if (v == null) {
            return false;
        }
        for (RunTaskStatusEnum e : values()) {
            if (e.name().equals(v)) {
                return true;
            }
        }
        return false;
    }
}
