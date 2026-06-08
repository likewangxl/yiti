package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程设计器-节点审批人规则实体，对应 WF_FLOW_NODE_APPROVER 表。
 * <p>
 * 每条记录代表某个节点的一条审批人规则，
 * 支持按角色(ROLE)、机构(ORG)、指定员工(EMP)等方式配置。
 * 与 WF_FLOW_NODE 通过 node_id 关联。
 * </p>
 */
@Data
@TableName("WF_FLOW_NODE_APPROVER")
public class WfFlowNodeApprover {

    /** 审批人规则ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属节点ID，对应 node_id */
    private String nodeId;

    /** 审批人类型：ROLE/ORG/EMP，对应 approver_type */
    private String approverType;

    /** 审批人值（层级角色=角色码/机构角色=机构码/指定人=工号/变量=变量名），对应 approver_value */
    private String approverValue;

    /** 层级（仅层级角色 LEVEL_ROLE）：SELF=发起机构 / PARENT=发起上级机构，对应 org_scope */
    private String orgScope;

    /** 可选角色码（仅机构角色 ORG_ROLE，空=该机构任一角色），对应 role_code */
    private String roleCode;

    /** 排序序号，对应 sort_no */
    private Integer sortNo;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;
}
