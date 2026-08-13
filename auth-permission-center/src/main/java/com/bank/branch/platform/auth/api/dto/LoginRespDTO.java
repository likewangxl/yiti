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
    /** 主机构编号（EXT_ORG_INFO.DEPT_NO），前端头部展示用 */
    private String deptNo;
    private List<RoleSimpleDTO> roles;
    private String token;
    /** 默认展示角色信息，roles 列表中置于首位，但不收窄权限 */
    private String primaryRoleId;
    private String primaryRoleCode;
    private String primaryRoleName;
}
