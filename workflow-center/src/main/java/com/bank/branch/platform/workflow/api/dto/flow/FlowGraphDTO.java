package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.Data;

import java.util.List;

/**
 * 审批流程图 DTO（顶层对象）。
 * <p>
 * 以 nodeKey 作为节点唯一标识，边通过 fromNodeKey/toNodeKey 引用节点，
 * 不依赖数据库自增 id，便于前端直接提交完整图结构。
 * </p>
 *
 * <pre>
 * 字段说明：
 *   name    - 流程模板名称
 *   bizType - 业务类型（如 ALLOC_ADJUST / TARGET_ADJUST），与 FlowVariableCatalog 对应
 *   nodes   - 节点列表
 *   edges   - 有向边列表
 * </pre>
 */
@Data
public class FlowGraphDTO {

    /** 流程模板名称 */
    private String name;

    /**
     * 业务类型，与 FlowVariableCatalog 白名单对应。
     * 取值如 ALLOC_ADJUST / TARGET_ADJUST。
     */
    private String bizType;

    /** 节点列表 */
    private List<FlowNodeDTO> nodes;

    /** 有向边列表 */
    private List<FlowEdgeDTO> edges;
}
