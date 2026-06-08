package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点候选人配置实体，对应 wf_node_candidate_conf 表。
 * <p>
 * 配置每个流程定义的每个用户任务节点的候选处理人规则。
 * 支持按角色(ROLE)、机构(ORG)、指定用户(USER)三种方式配置。
 * candidate_value 为 JSON 数组格式，如 ["BRANCH_HEAD"]。
 * </p>
 */
@Data
@TableName("WF_NODE_CANDIDATE_CONF")
public class WfNodeCandidateConf {

    /** 配置ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 流程定义KEY，对应 process_definition_key */
    private String processDefinitionKey;

    /** 节点KEY，对应 node_key */
    private String nodeKey;

    /** 候选类型：ROLE-角色, ORG-机构, USER-指定用户，对应 candidate_type */
    private String candidateType;

    /** 候选值（JSON数组），对应 candidate_value */
    private String candidateValue;

    /** 审批机构归属：SELF=本机构/PARENT=上级机构/NULL=不判断，对应 approve_org_scope */
    private String approveOrgScope;

    /** 机构角色固定机构码（机构角色审批人用，对应 org_code） */
    private String orgCode;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
