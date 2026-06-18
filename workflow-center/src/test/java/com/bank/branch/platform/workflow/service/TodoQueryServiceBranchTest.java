package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.workflow.api.dto.BranchOptionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TodoQueryService.computeOutgoingBranches 纯函数单测：
 * 给定流程图 + 当前 nodeKey，正确产出命名出边选项与路由变量。
 */
class TodoQueryServiceBranchTest {

    private FlowEdgeDTO edge(String from, String to, String name, String field, String val) {
        FlowEdgeDTO e = new FlowEdgeDTO();
        e.setFromNodeKey(from);
        e.setToNodeKey(to);
        e.setOutputName(name);
        e.setIsDefault(false);
        if (field != null) {
            FlowConditionDTO c = new FlowConditionDTO();
            c.setLogic("AND");
            c.setConditions(List.of(new FlowConditionDTO.Cond(field, "EQ", val)));
            e.setCondition(c);
        }
        return e;
    }

    @Test
    void computeOutgoingBranches_twoNamedEdges_returnsBothWithRouteVars() {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setEdges(List.of(
                edge("biz_dept_review", "biz_dept_leader_approve", "部门负责人审批", "corpRouteTo", "LEADER"),
                edge("biz_dept_review", "original_owner_approve", "原业绩所属人会签", "corpRouteTo", "OWNER"),
                // 结构边（无名）不应出现
                edge("biz_dept_review", "someGw", null, null, null)
        ));

        List<BranchOptionDTO> branches = TodoQueryService.computeOutgoingBranches(g, "biz_dept_review");

        assertEquals(2, branches.size());
        assertEquals("部门负责人审批", branches.get(0).getOutputName());
        assertEquals("LEADER", branches.get(0).getRouteVariables().get("corpRouteTo"));
        assertEquals("OWNER", branches.get(1).getRouteVariables().get("corpRouteTo"));
    }

    @Test
    void computeOutgoingBranches_otherNode_returnsEmpty() {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setEdges(List.of(edge("finance_review", "x", "资财部负责人审批", "finRouteTo", "LEADER")));
        assertTrue(TodoQueryService.computeOutgoingBranches(g, "biz_dept_review").isEmpty());
    }

    @Test
    void computeOutgoingBranches_nullGraph_returnsEmpty() {
        assertTrue(TodoQueryService.computeOutgoingBranches(null, "biz_dept_review").isEmpty());
    }
}
