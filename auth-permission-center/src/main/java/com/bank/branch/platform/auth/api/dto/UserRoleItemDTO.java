package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 用户角色关联DTO，用于批量查询用户角色时返回结果。
 * 包含用户ID和角色信息，方便按用户ID分组。
 */
@Data
public class UserRoleItemDTO {

    /** 用户ID（工号） */
    private String userId;

    /** 角色ID */
    private String roleId;

    /** 角色编码 */
    private String roleCode;

    /** 角色中文名 */
    private String roleChName;
}
