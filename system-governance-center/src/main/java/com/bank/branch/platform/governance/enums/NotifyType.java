package com.bank.branch.platform.governance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知类型枚举
 * 对应 user_notification.notify_type 字段
 */
@Getter
@AllArgsConstructor
public enum NotifyType {

    /** 系统通知 */
    SYSTEM("SYSTEM", "系统通知"),

    /** 流程通知 */
    WORKFLOW("WORKFLOW", "流程通知"),

    /** 业务通知 */
    BUSINESS("BUSINESS", "业务通知");

    private final String code;
    private final String description;
}
