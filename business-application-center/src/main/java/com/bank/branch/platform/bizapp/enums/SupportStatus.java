package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持申请状态枚举。
 * 对应 support_request.status 字段值。
 */
@Getter
@AllArgsConstructor
public enum SupportStatus {

    DRAFT("DRAFT", "草稿"),
    IN_APPROVAL("IN_APPROVAL", "审批中"),
    IN_PROGRESS("IN_PROGRESS", "办理中"),
    COMPLETED("COMPLETED", "已完成"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已撤回");

    private final String code;
    private final String description;
}
