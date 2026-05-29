package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批流程图发布前校验器。
 * <p>
 * 对 {@link FlowGraphDTO} 做结构合法性校验，所有违规项收集到
 * {@link ValidationResult#getErrors()} 中，不抛异常。
 * 调用方通过 {@link ValidationResult#isOk()} 判断是否可发布。
 * </p>
 *
 * <h3>校验规则</h3>
 * <ol>
 *   <li>START 节点恰好 1 个（0 个或多个均报错）</li>
 *   <li>至少 1 个 END 节点</li>
 *   <li>无孤立节点：除 START 外每个节点须有入边；除 END 外每个节点须有出边</li>
 *   <li>APPROVAL 节点 approvers 列表必须非空</li>
 *   <li>边条件字段须在 FlowVariableCatalog 白名单内，op 须为合法运算符</li>
 *   <li>边引用的 fromNodeKey / toNodeKey 须在 nodes 中存在</li>
 * </ol>
 */
@Component
public class FlowValidator {

    /** 合法运算符集合 */
    private static final Set<String> VALID_OPS = Set.of(
            "EQ", "NE", "GT", "GE", "LT", "LE", "IN", "NOT_IN", "CONTAINS");

    private final FlowVariableCatalog catalog;

    public FlowValidator(FlowVariableCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 校验流程图。
     *
     * @param graph   待校验的流程图 DTO（入参记录）
     * @param bizType 业务类型，用于查白名单（出参 / 耗时由调用方记录）
     * @return 校验结果；{@link ValidationResult#isOk()} 为 true 表示可发布
     */
    public ValidationResult validate(FlowGraphDTO graph, String bizType) {
        List<String> errors = new ArrayList<>();
        List<FlowNodeDTO> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<FlowEdgeDTO> edges = graph.getEdges() == null ? List.of() : graph.getEdges();

        // 构建辅助集合
        Set<String> nodeKeys = nodes.stream()
                .map(FlowNodeDTO::getNodeKey)
                .collect(Collectors.toSet());
        Set<String> nodesWithInEdge = edges.stream()
                .map(FlowEdgeDTO::getToNodeKey)
                .collect(Collectors.toSet());
        Set<String> nodesWithOutEdge = edges.stream()
                .map(FlowEdgeDTO::getFromNodeKey)
                .collect(Collectors.toSet());

        // ------------------------------------------------------------------
        // 规则 1：START 节点恰好 1 个
        // ------------------------------------------------------------------
        long startCount = nodes.stream()
                .filter(n -> "START".equals(n.getNodeType()))
                .count();
        if (startCount == 0) {
            errors.add("流程图缺少 START 节点，必须恰好有 1 个 START 节点");
        } else if (startCount > 1) {
            errors.add("流程图包含 " + startCount + " 个 START 节点，必须恰好有 1 个 START 节点");
        }

        // ------------------------------------------------------------------
        // 规则 2：至少 1 个 END 节点
        // ------------------------------------------------------------------
        long endCount = nodes.stream()
                .filter(n -> "END".equals(n.getNodeType()))
                .count();
        if (endCount == 0) {
            errors.add("流程图缺少 END 节点，至少需要 1 个 END 节点");
        }

        // ------------------------------------------------------------------
        // 规则 3：无孤立节点
        //   除 START 外每个节点须有入边
        //   除 END   外每个节点须有出边
        // ------------------------------------------------------------------
        for (FlowNodeDTO node : nodes) {
            String key = node.getNodeKey();
            String type = node.getNodeType();
            if (!"START".equals(type) && !nodesWithInEdge.contains(key)) {
                errors.add("节点 [" + key + "] 没有入边（除 START 节点外，每个节点都必须有入边）");
            }
            if (!"END".equals(type) && !nodesWithOutEdge.contains(key)) {
                errors.add("节点 [" + key + "] 没有出边（除 END 节点外，每个节点都必须有出边）");
            }
        }

        // ------------------------------------------------------------------
        // 规则 4：APPROVAL 节点 approvers 必须非空
        // ------------------------------------------------------------------
        for (FlowNodeDTO node : nodes) {
            if ("APPROVAL".equals(node.getNodeType())) {
                if (node.getApprovers() == null || node.getApprovers().isEmpty()) {
                    errors.add("APPROVAL 节点 [" + node.getNodeKey() + "] 的审批人列表为空，至少需要 1 个审批人");
                }
            }
        }

        // ------------------------------------------------------------------
        // 规则 5 & 6：边引用节点存在性 + 条件字段/op 合法性
        // ------------------------------------------------------------------
        Set<String> allowedFields = catalog.fields(bizType);
        for (FlowEdgeDTO edge : edges) {
            // 规则 6：fromNodeKey / toNodeKey 须存在于 nodes
            if (!nodeKeys.contains(edge.getFromNodeKey())) {
                errors.add("边 [" + edge.getFromNodeKey() + " → " + edge.getToNodeKey()
                        + "] 的 fromNodeKey [" + edge.getFromNodeKey() + "] 不在节点列表中");
            }
            if (!nodeKeys.contains(edge.getToNodeKey())) {
                errors.add("边 [" + edge.getFromNodeKey() + " → " + edge.getToNodeKey()
                        + "] 的 toNodeKey [" + edge.getToNodeKey() + "] 不在节点列表中");
            }

            // 规则 5：条件字段与 op 校验
            FlowConditionDTO condition = edge.getCondition();
            if (condition != null && condition.getConditions() != null
                    && !condition.getConditions().isEmpty()) {
                for (FlowConditionDTO.Cond cond : condition.getConditions()) {
                    if (cond.getField() == null || !allowedFields.contains(cond.getField())) {
                        errors.add("边 [" + edge.getFromNodeKey() + " → " + edge.getToNodeKey()
                                + "] 条件字段 [" + cond.getField() + "] 不在业务类型 [" + bizType
                                + "] 的白名单中（可用字段：" + allowedFields + "）");
                    }
                    if (cond.getOp() == null || !VALID_OPS.contains(cond.getOp())) {
                        errors.add("边 [" + edge.getFromNodeKey() + " → " + edge.getToNodeKey()
                                + "] 条件运算符 [" + cond.getOp() + "] 非法，合法运算符：" + VALID_OPS);
                    }
                }
            }
        }

        return new ValidationResult(errors);
    }

    // ---------------------------------------------------------------
    // 内部类：校验结果
    // ---------------------------------------------------------------

    /**
     * 流程图发布校验结果。
     * <p>
     * ok 为 true 表示 errors 列表为空，流程图可发布；
     * ok 为 false 表示存在一条或多条校验错误，需修正后重新提交。
     * </p>
     */
    public static class ValidationResult {

        private final List<String> errors;

        public ValidationResult(List<String> errors) {
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }

        /** 校验是否通过（errors 为空时返回 true） */
        public boolean isOk() {
            return errors.isEmpty();
        }

        /** 返回不可变的错误信息列表 */
        public List<String> getErrors() {
            return errors;
        }
    }
}
