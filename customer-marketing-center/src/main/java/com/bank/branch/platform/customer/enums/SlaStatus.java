package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SLA 状态枚举
 */
@Getter
@AllArgsConstructor
public enum SlaStatus {

    GREEN("GREEN", "正常"),
    YELLOW("YELLOW", "预警"),
    RED("RED", "超期");

    private final String code;
    private final String label;
}
