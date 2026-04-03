package com.bank.branch.platform.workflow.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SLA状态枚举
 */
@Getter
@AllArgsConstructor
public enum SlaStatus {

    GREEN("GREEN", "正常"),
    YELLOW("YELLOW", "预警"),
    RED("RED", "超时");

    private final String code;
    private final String description;
}
