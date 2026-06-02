package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 当前用户详情响应DTO
 * 用于前端获取当前登录用户完整权限信息
 */
@Data
public class CurrentUserRespDTO {

    private String empId;
    private String username;
    private String displayName;
    private String mainOrgCode;
    private String mainOrgName;
    private Integer orgLevel;
    private List<RoleSimpleDTO> roles;
    /** 当前激活角色ID（角色切换后为所切角色；roles 中该角色 primary=true） */
    private String activeRoleId;
    /** 拥有的资源URL列表 */
    private List<String> permissions;
    /** 业务类型 -> 数据范围 映射 */
    private Map<String, String> bizScopes;
    private Boolean isSystemAdmin;
}
