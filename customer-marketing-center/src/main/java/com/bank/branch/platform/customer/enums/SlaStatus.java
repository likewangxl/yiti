package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SLA 状态枚举
 */
@Getter
@AllArgsConstructor
public enum SlaStatus {

    BLUE("BLUE", "正常"),
    /** 兼容历史存量值，新任务统一使用 BLUE。 */
    GREEN("GREEN", "正常"),
    YELLOW("YELLOW", "预警"),
    RED("RED", "超期");

    private final String code;
    private final String label;
}
