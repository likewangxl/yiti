package com.bank.branch.platform.common.security.enums;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * 数据范围枚举
 * 定义数据权限的范围级别，用于控制用户可访问的数据边界
 */
@Getter
@AllArgsConstructor
public enum DataScopeType {
    SELF_CREATED("SELF_CREATED", "本人创建"),
    SELF("SELF", "本人相关"),
    SELF_ASSIGNED("SELF_ASSIGNED", "本人被分配"),
    ORG("ORG", "本机构"),
    ORG_SUBTREE("ORG_SUBTREE", "本机构及下属"),
    ALL("ALL", "全部数据"),
    WORKFLOW_PARTICIPANT("WORKFLOW_PARTICIPANT", "工作流参与人");

    private final String code;
    private final String description;
}
