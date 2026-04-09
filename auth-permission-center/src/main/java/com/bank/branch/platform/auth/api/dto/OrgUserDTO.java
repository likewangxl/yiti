package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 机构下用户简要信息DTO（G.2）
 * 用于 /api/orgs/{orgCode}/users 接口返回值
 * 对齐文档字段: empId, username, displayName, email, roles
 */
@Data
public class OrgUserDTO {

    /** 工号 */
    private String empId;

    /** 用户名 */
    private String username;

    /** 中文姓名 */
    private String displayName;

    /** 用户邮箱 */
    private String email;

    /** 角色列表 */
    private List<RoleSimpleDTO> roles;
}
