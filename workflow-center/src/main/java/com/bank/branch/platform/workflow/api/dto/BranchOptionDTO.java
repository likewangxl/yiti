package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 审批节点「下一步走向」分支选项 DTO。
 * <p>
 * 当当前审批节点在设计器图中存在 ≥2 条命名出边（流转连线 name）时，
 * 每条命名出边转为一个本 DTO 返给前端，供经办人选择走哪条分支；
 * 选中后前端把 {@link #routeVariables} 并入审批 formData，
 * 驱动发布后 BPMN 排他网关（如 {@code corpRouteTo}/{@code finRouteTo}）路由。
 * </p>
 */
@Data
public class BranchOptionDTO {

    /** 分支输出名称（WF_FLOW_EDGE.name，页面展示标签，如「部门负责人审批」） */
    private String outputName;

    /** 目标节点 nodeKey（展示/调试用） */
    private String toNodeKey;

    /** 是否默认分支（无条件，选它仅靠 BPMN 默认流转，routeVariables 为空） */
    private Boolean isDefault;

    /**
     * 选中该分支须写入审批 formData 的流程变量。
     * 由该出边 condition 中的 EQ 条件解析（field→value），如 {@code {"corpRouteTo":"LEADER"}}。
     */
    private Map<String, Object> routeVariables;
}
