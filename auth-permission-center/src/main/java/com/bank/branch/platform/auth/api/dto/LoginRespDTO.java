package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应DTO
 * 包含用户基本信息、主机构及角色列表
 */
@Data
public class LoginRespDTO {

    private String empId;
    private String username;
    private String displayName;
    private String mainOrgCode;
    private String mainOrgName;
    private List<RoleSimpleDTO> roles;
    private String token;
    /** 主角色（当前登录角色）信息，roles 列表中亦以主角色置于首位 */
    private String primaryRoleId;
    private String primaryRoleCode;
    private String primaryRoleName;
}
