package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlowValidator 单元测试（TDD 红-绿-重构）。
 * <p>
 * 所有测试均为纯 POJO，不启动 Spring 容器。
 * </p>
 */
class FlowValidatorTest {

    private FlowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FlowValidator(new FlowVariableCatalog());
    }

    // ---------------------------------------------------------------
    // Helper：构造节点
    // ---------------------------------------------------------------

    private FlowNodeDTO node(String nodeKey, String nodeType) {
        FlowNodeDTO n = new FlowNodeDTO();
        n.setNodeKey(nodeKey);
        n.setNodeType(nodeType);
        n.setName(nodeKey);
        return n;
    }

    private FlowNodeDTO approvalNode(String nodeKey, String approverType, String approverValue) {
        FlowNodeDTO n = node(nodeKey, "APPROVAL");
        n.setApproveMode("ANY");
        FlowApproverDTO approver = new FlowApproverDTO();
        approver.setApproverType(approverType);
        approver.setApproverValue(approverValue);
        n.setApprovers(List.of(approver));
        return n;
    }

    // ---------------------------------------------------------------
    // Helper：构造边
    // ---------------------------------------------------------------

    private FlowEdgeDTO edge(String from, String to) {
        FlowEdgeDTO e = new FlowEdgeDTO();
        e.setFromNodeKey(from);
        e.setToNodeKey(to);
        return e;
    }

    private FlowEdgeDTO edgeWithCondition(String from, String to,
                                          String field, String op, String value) {
        FlowEdgeDTO e = edge(from, to);
        FlowConditionDTO cond = new FlowConditionDTO();
        cond.setLogic("AND");
        FlowConditionDTO.Cond c = new FlowConditionDTO.Cond();
        c.setField(field);
        c.setOp(op);
        c.setValue(value);
        cond.setConditions(List.of(c));
        e.setCondition(cond);
        return e;
    }

    private FlowEdgeDTO defaultEdge(String from, String to) {
        FlowEdgeDTO e = edge(from, to);
        e.setIsDefault(true);
        return e;
    }

    // ---------------------------------------------------------------
    // Helper：构造完整图
    // ---------------------------------------------------------------

    /**
     * 最简线性图：start(START) → a1(APPROVAL) → end(END)
     */
    private FlowGraphDTO linearGraph() {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setName("测试流程");
        g.setBizType("ALLOC_ADJUST");
        g.setNodes(new ArrayList<>(List.of(
                node("start", "START"),
                approvalNode("a1", "ROLE", "CORP_DEPT"),
                node("end", "END")
        )));
        g.setEdges(new ArrayList<>(List.of(
                edge("start", "a1"),
                edge("a1", "end")
        )));
        return g;
    }

    // ---------------------------------------------------------------
    // 测试用例
    // ---------------------------------------------------------------

    /**
     * 合法线性图应通过校验。
     */
    @Test
    void valid_linear_passes() {
        FlowValidator.ValidationResult result = validator.validate(linearGraph(), "ALLOC_ADJUST");
        assertThat(result.isOk()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    /**
     * 无 START 节点时应报错，且错误信息含 "START"。
     */
    @Test
    void missing_start_fails() {
        FlowGraphDTO g = linearGraph();
        g.getNodes().removeIf(n -> "START".equals(n.getNodeType()));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("START"));
    }

    /**
     * 超过 1 个 START 节点时应报错。
     */
    @Test
    void multiple_start_fails() {
        FlowGraphDTO g = linearGraph();
        g.getNodes().add(node("start2", "START"));
        // 给新 start2 加出边，避免触发孤立节点规则干扰主断言
        g.getEdges().add(edge("start2", "a1"));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("START"));
    }

    /**
     * 无 END 节点时应报错。
     */
    @Test
    void no_end_fails() {
        FlowGraphDTO g = linearGraph();
        g.getNodes().removeIf(n -> "END".equals(n.getNodeType()));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("END"));
    }

    /**
     * APPROVAL 节点无审批人时应报错。
     */
    @Test
    void approval_without_approver_fails() {
        FlowGraphDTO g = linearGraph();
        // 找到 a1 节点，清空审批人
        g.getNodes().stream()
                .filter(n -> "a1".equals(n.getNodeKey()))
                .findFirst()
                .ifPresent(n -> n.setApprovers(List.of()));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isFalse();
    }

    /**
     * 孤立节点（既无入边也无出边）时应报错。
     */
    @Test
    void orphan_node_fails() {
        FlowGraphDTO g = linearGraph();
        // 添加孤立节点：没有任何边连接
        g.getNodes().add(node("orphan", "APPROVAL"));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isFalse();
    }

    /**
     * 边条件中字段为自定义变量（不在白名单）时应通过——路由变量由本流程运行时产出，无法预登记。
     */
    @Test
    void condition_customField_passes() {
        FlowGraphDTO g = linearGraph();
        // 替换 start→a1 边，加入自定义路由变量 corpRouteTo（不在 catalog 白名单）
        g.getEdges().set(0, edgeWithCondition("start", "a1", "corpRouteTo", "EQ", "LEADER"));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    /**
     * 边条件字段为空时仍应报错（仅放宽白名单，不放宽"字段必填"）。
     */
    @Test
    void condition_blankField_fails() {
        FlowGraphDTO g = linearGraph();
        g.getEdges().set(0, edgeWithCondition("start", "a1", null, "EQ", "X"));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isFalse();
    }

    /**
     * 边条件中字段在白名单、op 合法时应通过（gateway 分支场景）。
     * 图：start(START) → gw(GATEWAY) → a1(APPROVAL, 带条件 bizKind=EQ=CORP)
     *                               → end(END, 默认边)
     *     a1(APPROVAL) → end(END)
     */
    @Test
    void condition_valid_field_passes() {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setName("gateway 测试流程");
        g.setBizType("ALLOC_ADJUST");
        g.setNodes(new ArrayList<>(List.of(
                node("start", "START"),
                node("gw", "GATEWAY"),
                approvalNode("a1", "ROLE", "CORP_DEPT"),
                node("end", "END")
        )));
        g.setEdges(new ArrayList<>(List.of(
                edge("start", "gw"),
                edgeWithCondition("gw", "a1", "bizKind", "EQ", "CORP"),
                defaultEdge("gw", "end"),
                edge("a1", "end")
        )));
        FlowValidator.ValidationResult result = validator.validate(g, "ALLOC_ADJUST");
        assertThat(result.isOk()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }
}
