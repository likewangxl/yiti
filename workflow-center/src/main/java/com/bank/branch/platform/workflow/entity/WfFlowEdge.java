package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程设计器-连线/分支实体，对应 WF_FLOW_EDGE 表。
 * <p>
 * 每条记录代表某个流程定义中两个节点之间的一条连线（顺序流/条件分支）。
 * is_default=1 表示网关的默认分支；condition_json 存储条件表达式。
 * </p>
 */
@Data
@TableName("WF_FLOW_EDGE")
public class WfFlowEdge {

    /** 连线ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属流程定义ID，对应 flow_def_id */
    private String flowDefId;

    /** 来源节点ID，对应 from_node_id */
    private String fromNodeId;

    /** 目标节点ID，对应 to_node_id */
    private String toNodeId;

    /** 连线名称（条件分支描述），对应 name */
    private String name;

    /** 是否为默认分支：0-否，1-是，对应 is_default */
    private Integer isDefault;

    /** 条件表达式（JSON格式），对应 condition_json */
    private String conditionJson;

    /** 排序序号，对应 sort_no */
    private Integer sortNo;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;
}
