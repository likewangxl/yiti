package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色详情响应DTO
 */
@Data
public class RoleRespDTO {

    private String roleId;
    private String roleCode;
    private String roleChName;
    private Integer recordStatus;
    private String sysCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
    /** 绑定该角色的用户数量 */
    private Integer userCount;
}
