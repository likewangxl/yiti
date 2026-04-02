package com.bank.branch.platform.governance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务调度配置状态枚举
 */
@Getter
@AllArgsConstructor
public enum JobStatus {

    ACTIVE("ACTIVE", "运行中"),
    PAUSED("PAUSED", "已暂停");

    private final String code;
    private final String label;
}
