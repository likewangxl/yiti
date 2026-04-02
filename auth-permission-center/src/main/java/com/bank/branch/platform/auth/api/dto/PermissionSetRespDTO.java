package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 用户权限集合响应DTO
 * 聚合用户的所有权限信息，供网关或前端做完整权限判断
 */
@Data
public class PermissionSetRespDTO {

    /** 用户可访问的资源URL集合 */
    private Set<String> resourceUrls;

    /** 业务类型 -> 数据范围 映射 */
    private Map<String, String> bizScopes;

    /** 用户角色ID集合 */
    private Set<String> roleIds;

    /** 用户角色编码集合 */
    private Set<String> roleCodes;

    private Boolean isSystemAdmin;
}
