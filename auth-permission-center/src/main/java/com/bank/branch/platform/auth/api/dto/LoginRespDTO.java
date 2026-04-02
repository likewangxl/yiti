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
}
