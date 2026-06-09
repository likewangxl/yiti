package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowBpmnGenerator} 单元测试。
 * <p>
 * 验证流程图模型经 Flowable 7 {@code org.flowable.bpmn.model.*} Java API
 * 编程式构建为 {@link BpmnModel} 后的等价结构：UserTask 监听器、会签 MI、
 * 审批结果网关驳回路径、网关条件渲染、流程级完成监听器及可序列化性。
 * </p>
 */
class FlowBpmnGeneratorTest {

    /** 被测对象，注入真实的条件表达式生成器（行为已由其自身测试覆盖） */
    private final FlowBpmnGenerator generator =
            new FlowBpmnGenerator(new FlowConditionExpressionBuilder());

    private static final String SHADOW_KEY = "wf_shadow_demo";

    /* ====================== helper：构造 FlowGraphDTO 片段 ====================== */

    /** 构造一个节点（不含审批人） */
    private FlowNodeDTO node(String key, String type, String name) {
        FlowNodeDTO n = new FlowNodeDTO();
        n.setNodeKey(key);
        n.setNodeType(type);
        n.setName(name);
        return n;
    }

    /** 构造一个审批节点（带模式与一个审批人） */
    private FlowNodeDTO approval(String key, String name, String mode) {
        FlowNodeDTO n = node(key, "APPROVAL", name);
        n.setApproveMode(mode);
        FlowApproverDTO a = new FlowApproverDTO();
        a.setApproverType("ROLE");
        a.setApproverValue("CORP_DEPT");
        n.setApprovers(Collections.singletonList(a));
        return n;
    }

    /** 构造一条普通边 */
    private FlowEdgeDTO edge(String from, String to) {
        FlowEdgeDTO e = new FlowEdgeDTO();
        e.setFromNodeKey(from);
        e.setToNodeKey(to);
        return e;
    }

    /** 构造一个 graph */
    private FlowGraphDTO graph(String name, List<FlowNodeDTO> nodes, List<FlowEdgeDTO> edges) {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setName(name);
        g.setBizType("ALLOC_ADJUST");
        g.setNodes(nodes);
        g.setEdges(edges);
        return g;
    }

    /** 构造线性图 start → a1 → end（a1 为指定审批模式的审批节点） */
    private FlowGraphDTO linearGraph(String mode) {
        return graph("线性审批流",
                Arrays.asList(
                        node("start", "START", "开始"),
                        approval("a1", "审批节点", mode),
                        node("end", "END", "结束")),
                Arrays.asList(edge("start", "a1"), edge("a1", "end")));
    }

    /* ====================== 测试用例 ====================== */

    @Test
    void any_node_generates_userTask_with_listener() {
        BpmnModel model = generator.generate(linearGraph("ANY"), SHADOW_KEY);
        Process process = model.getProcessById(SHADOW_KEY);

        FlowElement a1 = process.getFlowElement("a1");
        assertTrue(a1 instanceof UserTask, "a1 应为 UserTask");
        UserTask ut = (UserTask) a1;

        // 任务监听器：create 事件 + ${taskAssignmentListener}
        boolean hasTaskListener = ut.getTaskListeners().stream()
                .anyMatch(l -> "${taskAssignmentListener}".equals(l.getImplementation()));
        assertTrue(hasTaskListener, "应挂 ${taskAssignmentListener} 任务监听器");

        // 或签：无多实例
        assertNull(ut.getLoopCharacteristics(), "ANY 模式 userTask 不应有多实例特性");
    }

    @Test
    void all_node_generates_multiInstance() {
        BpmnModel model = generator.generate(linearGraph("ALL"), SHADOW_KEY);
        Process process = model.getProcessById(SHADOW_KEY);

        UserTask ut = (UserTask) process.getFlowElement("a1");
        MultiInstanceLoopCharacteristics mi = ut.getLoopCharacteristics();
        assertNotNull(mi, "ALL 模式应有多实例特性");
        // 集合变量名 approverEmpIds 应出现在 inputDataItem 或 collection 上
        String combined = "" + mi.getInputDataItem() + "|" + mi.getCollectionString();
        assertTrue(combined.contains("approverEmpIds"),
                "MI 集合应引用 approverEmpIds，实际: " + combined);
    }

    @Test
    void approval_node_has_reject_path() {
        BpmnModel model = generator.generate(linearGraph("ANY"), SHADOW_KEY);
        Process process = model.getProcessById(SHADOW_KEY);

        // 驳回汇聚 EndEvent
        FlowElement rejectedEnd = process.getFlowElement("rejectedEnd");
        assertTrue(rejectedEnd instanceof EndEvent, "应存在 rejectedEnd EndEvent");

        // 审批结果网关
        FlowElement gw = process.getFlowElement("a1_approveGw");
        assertTrue(gw instanceof ExclusiveGateway, "应存在 a1_approveGw 排他网关");

        // 网关 → rejectedEnd 的驳回边，条件含 approved == false
        boolean hasRejectFlow = process.getFlowElements().stream()
                .filter(fe -> fe instanceof SequenceFlow)
                .map(fe -> (SequenceFlow) fe)
                .anyMatch(sf -> "a1_approveGw".equals(sf.getSourceRef())
                        && "rejectedEnd".equals(sf.getTargetRef())
                        && sf.getConditionExpression() != null
                        && sf.getConditionExpression().contains("approved == false"));
        assertTrue(hasRejectFlow, "应存在 a1_approveGw → rejectedEnd 且条件含 approved == false 的边");
    }

    @Test
    void gateway_edge_condition_rendered() {
        // start → gw(GATEWAY) → a(条件 bizKind EQ CORP) / → end(default)
        FlowConditionDTO cond = new FlowConditionDTO();
        cond.setLogic("AND");
        cond.setConditions(Collections.singletonList(
                new FlowConditionDTO.Cond("bizKind", "EQ", "CORP")));
        FlowEdgeDTO gwToA = edge("gw", "a");
        gwToA.setCondition(cond);
        FlowEdgeDTO gwToEnd = edge("gw", "end");
        gwToEnd.setIsDefault(true);

        FlowGraphDTO g = graph("网关审批流",
                Arrays.asList(
                        node("start", "START", "开始"),
                        node("gw", "GATEWAY", "排他网关"),
                        node("a", "APPROVAL", "审批"),
                        node("end", "END", "结束")),
                Arrays.asList(edge("start", "gw"), gwToA, gwToEnd, edge("a", "end")));
        // a 设为审批节点（带审批人）
        ((FlowNodeDTO) g.getNodes().get(2)).setApproveMode("ANY");
        FlowApproverDTO ap = new FlowApproverDTO();
        ap.setApproverType("ROLE");
        ap.setApproverValue("CORP_DEPT");
        ((FlowNodeDTO) g.getNodes().get(2)).setApprovers(Collections.singletonList(ap));

        BpmnModel model = generator.generate(g, SHADOW_KEY);
        Process process = model.getProcessById(SHADOW_KEY);

        SequenceFlow gwA = process.getFlowElements().stream()
                .filter(fe -> fe instanceof SequenceFlow)
                .map(fe -> (SequenceFlow) fe)
                .filter(sf -> "gw".equals(sf.getSourceRef()) && "a".equals(sf.getTargetRef()))
                .findFirst().orElse(null);
        assertNotNull(gwA, "应存在 gw → a 的边");
        assertEquals("${bizKind == 'CORP'}", gwA.getConditionExpression());
    }

    @Test
    void edge_outputName_rendered_as_sequenceFlow_name() {
        // 网关出边带「输出名称」→ 生成的 SequenceFlow.name 等于输出名称（带入已部署 BPMN）
        FlowEdgeDTO gwToA = edge("gw", "a");
        gwToA.setOutputName("提交部门负责人");
        FlowGraphDTO g = graph("走向命名流",
                Arrays.asList(
                        node("start", "START", "开始"),
                        node("gw", "GATEWAY", "网关"),
                        node("a", "APPROVAL", "审批"),
                        node("end", "END", "结束")),
                Arrays.asList(edge("start", "gw"), gwToA, edge("a", "end")));
        ((FlowNodeDTO) g.getNodes().get(2)).setApproveMode("ANY");
        FlowApproverDTO ap = new FlowApproverDTO();
        ap.setApproverType("ROLE");
        ap.setApproverValue("CORP_DEPT");
        ((FlowNodeDTO) g.getNodes().get(2)).setApprovers(Collections.singletonList(ap));

        BpmnModel model = generator.generate(g, SHADOW_KEY);
        Process process = model.getProcessById(SHADOW_KEY);

        SequenceFlow gwA = process.getFlowElements().stream()
                .filter(fe -> fe instanceof SequenceFlow)
                .map(fe -> (SequenceFlow) fe)
                .filter(sf -> "gw".equals(sf.getSourceRef()) && "a".equals(sf.getTargetRef()))
                .findFirst().orElse(null);
        assertNotNull(gwA, "应存在 gw → a 的边");
        assertEquals("提交部门负责人", gwA.getName());
    }

    @Test
    void model_is_deployable() {
        BpmnModel model = generator.generate(linearGraph("ANY"), SHADOW_KEY);
        byte[] xml = new BpmnXMLConverter().convertToXML(model);
        assertNotNull(xml, "序列化结果不应为 null");
        assertTrue(xml.length > 0, "序列化结果应非空");
    }

    @Test
    void process_has_completed_listener() {
        BpmnModel model = generator.generate(linearGraph("ANY"), SHADOW_KEY);
        Process process = model.getProcessById(SHADOW_KEY);

        boolean hasCompleted = process.getExecutionListeners().stream()
                .anyMatch(l -> "${processCompletedListener}".equals(l.getImplementation())
                        && "end".equals(l.getEvent()));
        assertTrue(hasCompleted, "流程级应有 end 事件的 ${processCompletedListener}");
    }
}
