package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.Data;

import java.util.List;

/**
 * 审批流程节点 DTO。
 * <p>
 * nodeKey：节点唯一标识（图内唯一，作为边引用 fromNodeKey/toNodeKey 的锚点）<br>
 * nodeType：节点类型，取值 START / END / APPROVAL / GATEWAY<br>
 * name：节点中文名称<br>
 * approveMode：审批模式，APPROVAL 节点有效，取值 ALL（会签）/ ANY（或签）<br>
 * sortNo：节点排列序号，供前端渲染参考<br>
 * approvers：APPROVAL 节点的审批人列表，非 APPROVAL 节点可为空
 * </p>
 */
@Data
public class FlowNodeDTO {

    /** 节点唯一标识，图内唯一，不可重复 */
    private String nodeKey;

    /** 节点类型：START / END / APPROVAL / GATEWAY */
    private String nodeType;

    /** 节点中文名称 */
    private String name;

    /** 审批模式（仅 APPROVAL 节点有效）：ALL（会签）/ ANY（或签） */
    private String approveMode;

    /** 排列序号，供前端渲染参考 */
    private Integer sortNo;

    /** 审批人配置列表（APPROVAL 节点必须非空） */
    private List<FlowApproverDTO> approvers;
}
