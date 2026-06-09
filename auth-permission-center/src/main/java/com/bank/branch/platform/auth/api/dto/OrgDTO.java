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
    /** 机构编号（来自 EXT_ORG_INFO.DEPT_NO / xanpd sys_dept.DEPT_NO） */
    private String deptNo;
    private Integer orgLevel;
    /** 父机构编码，对应数据库 p_id 字段 */
    private String parentOrgCode;
    private Integer organState;
}
