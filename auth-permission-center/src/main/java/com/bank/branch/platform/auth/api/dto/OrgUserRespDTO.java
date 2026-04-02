package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 机构下用户响应DTO
 * 用于查询某机构下的用户列表及其角色信息
 */
@Data
public class OrgUserRespDTO {

    private String empId;
    private String username;
    private String displayName;
    private String email;
    private List<RoleSimpleDTO> roles;
}
