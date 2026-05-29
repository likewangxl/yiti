package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;
import java.util.List;

/** 人员标签列表的一行：人员基本信息 + RBAC 角色 + 被评价人角色（单） + 评价人角色（多）。 */
@Data
public class EvalUserRoleRowDTO {
    /** 人员ID（工号，PT_USER.USER_ID）. */
    private String userId;
    /** 显示名（中文名优先，回退登录名）. */
    private String userName;
    /** 部门（机构名，来自通讯录）. */
    private String orgName;
    /** 岗位（来自通讯录 position）. */
    private String position;
    /** RBAC 角色中文名列表（只读展示）. */
    private List<String> roleNames;
    /** 被评价人角色（tagType=1，至多一个；无则 null）. */
    private EvalUserTagBriefDTO beEvalTag;
    /** 评价人角色（tagType=2，可多个）. */
    private List<EvalUserTagBriefDTO> evalTags;
    /** 是否启用评价：1=是 0=否. */
    private Integer evalEnabled;
}
