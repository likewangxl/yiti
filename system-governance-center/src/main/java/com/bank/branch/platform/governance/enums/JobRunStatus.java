package com.bank.branch.platform.governance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务执行日志状态枚举
 */
@Getter
@AllArgsConstructor
public enum JobRunStatus {

    RUNNING("RUNNING", "执行中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;
}
