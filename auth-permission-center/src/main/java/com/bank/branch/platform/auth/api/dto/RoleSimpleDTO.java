package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 角色简要信息DTO
 * 用于嵌套在其他响应体中的角色轻量表示
 */
@Data
public class RoleSimpleDTO {
    private String roleId;
    private String roleCode;
    private String roleChName;
    /** 是否默认展示角色（PT_USER_ROLE.DEFAULT_ASSIGN），不影响权限计算 */
    private Boolean primary;
}
