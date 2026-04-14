package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持场景枚举。
 * A=产品直达（有明确产品，无其他需求），B=部门承接（含模糊需求）。
 */
@Getter
@AllArgsConstructor
public enum SupportScenario {

    A("A", "产品直达", "support_simple_v1"),
    B("B", "部门承接", "support_complex_v1");

    private final String code;
    private final String description;
    private final String processDefinitionKey;
}
