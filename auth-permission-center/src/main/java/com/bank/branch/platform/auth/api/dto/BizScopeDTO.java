package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 业务范围DTO
 * 用于跨模块传递角色业务数据范围配置信息
 */
@Data
public class BizScopeDTO {
    private String id;
    private String roleId;
    private String bizType;
    private String dataScope;
    private Integer recordStatus;
}
