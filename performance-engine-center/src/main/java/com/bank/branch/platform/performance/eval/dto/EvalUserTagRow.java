package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

/** EVAL_USER_TAG JOIN EVAL_TAG 的批量查询投影行. */
@Data
public class EvalUserTagRow {
    /** 人员ID（EVAL_USER_TAG.user_id，工号）. */
    private String userId;
    /** 标签ID. */
    private Long tagId;
    /** 标签名称. */
    private String tagName;
    /** 角色类型：1=被评价角色, 2=评价角色. */
    private Integer roleType;
}
