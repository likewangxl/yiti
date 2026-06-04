package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 组织机构树节点DTO
 * 用于前端展示机构树形结构
 */
@Data
public class OrgTreeNodeDTO {

    private String orgCode;
    private String orgName;
    private Integer orgLevel;
    private String parentOrgCode;
    private Integer organState;
    /** 部门编号（来自 xanpd sys_dept.DEPT_NO） */
    private String deptNo;
    /** 子机构节点列表 */
    private List<OrgTreeNodeDTO> children;
}
