package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持来源类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SupportSourceType {

    EXISTING_CUSTOMER("EXISTING_CUSTOMER", "存量客户"),
    TOUCH_TASK("TOUCH_TASK", "触达任务转入");

    private final String code;
    private final String description;
}
