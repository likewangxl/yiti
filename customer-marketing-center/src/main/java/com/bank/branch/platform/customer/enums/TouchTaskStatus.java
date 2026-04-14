package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 触达任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum TouchTaskStatus {

    PENDING("PENDING", "待处理"),
    SUCCESS("SUCCESS", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;
}
