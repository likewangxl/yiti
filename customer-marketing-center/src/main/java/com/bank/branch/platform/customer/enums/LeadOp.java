package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 线索操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum LeadOp {

    CREATE("CREATE", "新建"),
    UPDATE("UPDATE", "修改"),
    DELETE("DELETE", "删除");

    private final String code;
    private final String label;
}
