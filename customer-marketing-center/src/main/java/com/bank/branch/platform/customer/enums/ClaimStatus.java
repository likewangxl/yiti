package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认领状态枚举
 */
@Getter
@AllArgsConstructor
public enum ClaimStatus {

    CLAIMED("CLAIMED", "已认领"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;
}
