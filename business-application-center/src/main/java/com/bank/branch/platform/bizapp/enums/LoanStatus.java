package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资产投放申请状态枚举。
 * 对应 loan_apply.status 字段值。
 */
@Getter
@AllArgsConstructor
public enum LoanStatus {

    DRAFT("DRAFT", "草稿"),
    IN_APPROVAL("IN_APPROVAL", "审批中"),
    COMPLETED("COMPLETED", "已完成"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已撤回");

    private final String code;
    private final String description;
}
