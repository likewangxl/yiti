package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导入批次状态枚举
 */
@Getter
@AllArgsConstructor
public enum BatchStatus {

    CREATED("CREATED", "已创建"),
    VALIDATION_FAILED("VALIDATION_FAILED", "校验失败"),
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String label;
}
