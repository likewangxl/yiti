package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户主档状态枚举
 */
@Getter
@AllArgsConstructor
public enum CustMasterStatus {

    ACTIVE("ACTIVE", "活跃"),
    INACTIVE("INACTIVE", "停用");

    private final String code;
    private final String label;
}
