package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 触达任务类型枚举
 */
@Getter
@AllArgsConstructor
public enum TouchTaskType {

    FIRST_TOUCH("FIRST_TOUCH", "首次触达"),
    FOLLOW_UP("FOLLOW_UP", "后续跟进");

    private final String code;
    private final String label;
}
