package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程设计器-节点实体，对应 WF_FLOW_NODE 表。
 * <p>
 * 每条记录代表某个流程定义中的一个节点（开始/审批/网关/结束等）。
 * 与 WF_FLOW_DEF 通过 flow_def_id 关联，与 WF_FLOW_NODE_APPROVER 通过 id 关联。
 * </p>
 */
@Data
@TableName("WF_FLOW_NODE")
public class WfFlowNode {

    /** 节点ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属流程定义ID，对应 flow_def_id */
    private String flowDefId;

    /** 节点标识键（在流程内唯一），对应 node_key */
    private String nodeKey;

    /** 节点类型：START/APPROVE/GATEWAY/END，对应 node_type */
    private String nodeType;

    /** 节点显示名称，对应 name */
    private String name;

    /** 审批模式：ANY/ALL，对应 approve_mode */
    private String approveMode;

    /** 排序序号，对应 sort_no */
    private Integer sortNo;

    /** 节点在画布上的 X 坐标，对应 pos_x */
    private Integer posX;

    /** 节点在画布上的 Y 坐标，对应 pos_y */
    private Integer posY;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
