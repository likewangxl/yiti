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

    /** 审批人类型：LEVEL_ROLE（层级角色）/ ORG_ROLE（机构角色）/ USER（指定人）/ VAR（流程变量） */
    private String approverType;

    /** 审批人主值：层级角色=角色码 / 机构角色=机构码 / 指定人=工号 / 变量=变量名 */
    private String approverValue;

    /** 层级（仅层级角色）：SELF=发起机构 / PARENT=发起上级机构 */
    private String orgScope;

    /** 可选角色码（仅机构角色，空=该机构任一角色） */
    private String roleCode;
}
