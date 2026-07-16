package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 用户部门名称行（EXT_USER_ORG JOIN EXT_ORG_INFO 的批量查询结果）。
 * <p>供用户列表页批量填充「部门」列；用户挂多机构时 deptName 已在 SQL 端聚合为「名称1、名称2」。</p>
 */
@Data
public class UserDeptNameDTO {

    /** 用户ID（PT_USER.USER_ID / EXT_USER_ORG.USER_ID） */
    private String userId;

    /** 部门（机构）名称，即 EXT_ORG_INFO.ORG_NAME；多机构以「、」连接 */
    private String deptName;
}
