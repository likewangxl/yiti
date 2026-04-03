package com.bank.branch.platform.workflow.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流程实例状态枚举
 */
@Getter
@AllArgsConstructor
public enum ProcessStatus {

    RUNNING("RUNNING", "运行中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;
}
