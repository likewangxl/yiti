package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.Data;

/**
 * 审批节点审批人配置 DTO。
 * <p>
 * approverType：审批人类型，如 ROLE（角色）、ORG（组织）、USER（指定人）<br>
 * approverValue：审批人值，与 candidateType 对应（角色编码 / 部门编码 / 员工编号）
 * </p>
 */
@Data
public class FlowApproverDTO {

    /** 审批人类型：ROLE / ORG / USER */
    private String approverType;

    /** 审批人值（角色编码 / 部门编码 / 员工编号） */
    private String approverValue;
}
