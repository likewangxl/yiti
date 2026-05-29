package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.Data;

/**
 * 审批流程有向边 DTO。
 * <p>
 * fromNodeKey：源节点 nodeKey<br>
 * toNodeKey  ：目标节点 nodeKey<br>
 * isDefault  ：是否为默认分支（GATEWAY 出边中，当无条件匹配时走默认边）<br>
 * condition  ：分支条件；isDefault=true 或无条件时可为 null
 * </p>
 */
@Data
public class FlowEdgeDTO {

    /** 源节点 nodeKey */
    private String fromNodeKey;

    /** 目标节点 nodeKey */
    private String toNodeKey;

    /** 是否为默认分支（GATEWAY 无条件匹配时使用） */
    private Boolean isDefault;

    /** 分支条件，无条件时为 null */
    private FlowConditionDTO condition;
}
