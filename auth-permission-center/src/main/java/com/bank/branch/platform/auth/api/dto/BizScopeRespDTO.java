package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务范围详情响应DTO
 */
@Data
public class BizScopeRespDTO {

    private String id;
    private String roleId;
    private String roleChName;
    private String bizType;
    private String dataScope;
    private Integer recordStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
