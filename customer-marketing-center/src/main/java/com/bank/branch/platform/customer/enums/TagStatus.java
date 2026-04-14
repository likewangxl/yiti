package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签状态枚举
 */
@Getter
@AllArgsConstructor
public enum TagStatus {

    ACTIVE("ACTIVE", "启用"),
    DISABLED("DISABLED", "停用");

    private final String code;
    private final String label;
}
