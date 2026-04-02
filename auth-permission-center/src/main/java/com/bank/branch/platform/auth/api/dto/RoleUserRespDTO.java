package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色下用户响应DTO
 * 用于查询某角色绑定的用户列表
 */
@Data
public class RoleUserRespDTO {

    private String empId;
    private String username;
    private String displayName;
    private String orgCode;
    private String orgName;
    /** 用户绑定到该角色的时间 */
    private LocalDateTime bindTime;
}
