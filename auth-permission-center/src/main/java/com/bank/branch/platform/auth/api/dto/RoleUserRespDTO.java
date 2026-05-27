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
    /** 账号是否启用：0-启用，1-未启用（对应 PT_USER.ISENABLED） */
    private Integer isEnabled;
    /** 用户绑定到该角色的时间 */
    private LocalDateTime bindTime;
}
