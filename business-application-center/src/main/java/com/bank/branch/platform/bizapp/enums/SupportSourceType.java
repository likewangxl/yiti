package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持来源类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SupportSourceType {

    MANUAL("MANUAL", "手动创建"),
    TOUCH_TASK("TOUCH_TASK", "触达任务转入");

    private final String code;
    private final String description;
}
