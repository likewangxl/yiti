package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 组织机构DTO
 * 用于跨模块传递组织机构基本信息
 */
@Data
public class OrgDTO {
    private String orgCode;
    private String orgName;
    private Integer orgLevel;
    /** 父机构编码，对应数据库 p_id 字段 */
    private String parentOrgCode;
    private Integer organState;
}
