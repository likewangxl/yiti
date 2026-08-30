package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审批流程图 BPMN 生成器。
 * <p>
 * 将前端提交的流程图模型（{@link FlowGraphDTO}）用 Flowable 7 的
 * {@code org.flowable.bpmn.model.*} Java API 编程式构建成 {@link BpmnModel}，
 * 生成结果与现有静态 BPMN（如 perf_alloc_adjust_corp_v1.bpmn20.xml）等价：
 * </p>
 * <ul>
 *   <li>流程级 ExecutionListener(event=end, ${processCompletedListener})；</li>
 *   <li>每个审批 UserTask 挂 TaskListener(event=create, ${taskAssignmentListener})；</li>
 *   <li>会签（ALL）审批节点设多实例 MI（集合变量 approverEmpIds），
 *       并挂 ExecutionListener(event=start, ${multiInstanceApproverResolver}) 运行时解析集合；</li>
 *   <li>按机构会签（GROUP_ALL）审批节点设顺序多实例 MI（集合变量 approverGroups），
 *       每个元素为一个机构审批组，组内候选人由任务监听器实现或签；</li>
 *   <li>每个审批节点后插入审批结果排他网关，统一引出 approved==false 的驳回路径
 *       到单例 rejectedEnd；</li>
 *   <li>网关出边按原图边的条件渲染（{@link FlowConditionExpressionBuilder}）。</li>
 * </ul>
 *
 * <p>
 * 设计要点（为什么这样接线）：审批节点的真实后继不直接连原 target，而是先连到
 * 该节点的审批结果网关 {@code <nodeKey>_approveGw}，再由网关按"通过 / 驳回"两类
 * 出边路由——通过路径承接原图中以该审批节点为 from 的边（叠加 approved==true），
 * 驳回路径统一汇聚到 rejectedEnd。这样把"审批人是否通过"的分流逻辑显式落到网关上，
 * 避免散落在每条业务边里。
 * </p>
 */
@Component
public class FlowBpmnGenerator {

    /** 驳回汇聚终点的固定 id（单例，全流程共用） */
    private static final String REJECTED_END_ID = "rejectedEnd";

    /** 会签多实例的集合变量名（运行时由 ${multiInstanceApproverResolver} 注入审批人 empId 列表） */
    private static final String MI_COLLECTION = "approverEmpIds";

    /** 会签多实例元素变量名（单个审批人 empId） */
    private static final String MI_ELEMENT_VAR = "approver";

    /** 按机构会签的集合变量名（由分组解析监听器注入机构审批组快照） */
    private static final String MI_GROUP_COLLECTION = "approverGroups";

    /** 按机构会签的多实例元素变量名（当前机构审批组） */
    private static final String MI_GROUP_ELEMENT_VAR = "approverGroup";

    private final FlowConditionExpressionBuilder conditionBuilder;

    public FlowBpmnGenerator(FlowConditionExpressionBuilder conditionBuilder) {
        this.conditionBuilder = conditionBuilder;
    }

    /**
     * 将流程图模型生成为 Flowable {@link BpmnModel}。
     *
     * @param graph            流程图模型（节点 + 边）
     * @param shadowProcessKey 影子流程 key，作为 Process 的 id
     * @return 可被 {@code BpmnXMLConverter} 序列化、可部署的 BpmnModel
     */
    public BpmnModel generate(FlowGraphDTO graph, String shadowProcessKey) {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId(shadowProcessKey);
        process.setName(graph.getName());
        process.setExecutable(true);
        model.addProcess(process);

        // 1. 流程级完成监听器：流程实例结束时回调 ${processCompletedListener}
        process.getExecutionListeners().add(
                delegateListener("end", "${processCompletedListener}"));

        List<FlowNodeDTO> nodes = graph.getNodes() == null
                ? Collections.emptyList() : graph.getNodes();
        List<FlowEdgeDTO> edges = graph.getEdges() == null
                ? Collections.emptyList() : graph.getEdges();

        // 节点类型索引，供后续接线时快速判断 from/to 节点类型
        Map<String, FlowNodeDTO> nodeByKey = new HashMap<>();
        for (FlowNodeDTO n : nodes) {
            nodeByKey.put(n.getNodeKey(), n);
        }

        // 2. 生成各节点对应的 FlowElement
        boolean hasRejectedEnd = false;
        Set<String> approvalKeys = new HashSet<>();
        for (FlowNodeDTO n : nodes) {
            switch (n.getNodeType()) {
                case "START":
                    process.addFlowElement(named(new StartEvent(), n));
                    break;
                case "END":
                    if (REJECTED_END_ID.equals(n.getNodeKey())) {
                        hasRejectedEnd = true;
                    }
                    process.addFlowElement(named(new EndEvent(), n));
                    break;
                case "GATEWAY":
                    process.addFlowElement(named(new ExclusiveGateway(), n));
                    break;
                case "APPROVAL":
                    approvalKeys.add(n.getNodeKey());
                    process.addFlowElement(buildUserTask(n));
                    // 审批结果网关
                    ExclusiveGateway gw = new ExclusiveGateway();
                    gw.setId(approveGwId(n.getNodeKey()));
                    gw.setName(n.getName() + "-审批结果");
                    process.addFlowElement(gw);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的节点类型: " + n.getNodeType());
            }
        }

        // 3. 驳回汇聚 EndEvent（单例；若 graph 自身已有同 id 则复用，不重复添加）
        if (!hasRejectedEnd) {
            EndEvent rejectedEnd = new EndEvent();
            rejectedEnd.setId(REJECTED_END_ID);
            rejectedEnd.setName("驳回结束");
            process.addFlowElement(rejectedEnd);
        }

        // 4. 接线
        AtomicInteger flowSeq = new AtomicInteger(0);
        for (FlowNodeDTO n : approvalKeys.isEmpty() ? Collections.<FlowNodeDTO>emptyList() : nodes) {
            // 审批节点 → 其审批结果网关（无条件直连）
            if ("APPROVAL".equals(n.getNodeType())) {
                process.addFlowElement(sequenceFlow(
                        flowSeq, n.getNodeKey(), approveGwId(n.getNodeKey()), null));
            }
        }
        // 审批结果网关统一引出 approved==false 的驳回边
        for (String approvalKey : approvalKeys) {
            SequenceFlow reject = sequenceFlow(
                    flowSeq, approveGwId(approvalKey), REJECTED_END_ID, "${approved == false}");
            process.addFlowElement(reject);
        }

        // 处理原图各边
        for (FlowEdgeDTO e : edges) {
            String from = e.getFromNodeKey();
            String to = e.getToNodeKey();
            FlowNodeDTO fromNode = nodeByKey.get(from);
            boolean fromIsApproval = fromNode != null && "APPROVAL".equals(fromNode.getNodeType());

            SequenceFlow sf;
            if (fromIsApproval) {
                // 审批节点出边改由其审批结果网关引出，叠加 approved==true（通过路径）
                String passCond = combineApprovedTrue(conditionBuilder.toEl(e.getCondition()));
                sf = sequenceFlow(flowSeq, approveGwId(from), to, passCond);
                process.addFlowElement(sf);
            } else {
                // 普通边 / 网关出边：直接生成，条件按原图渲染
                String cond = conditionBuilder.toEl(e.getCondition());
                sf = sequenceFlow(flowSeq, from, to, cond);
                process.addFlowElement(sf);
                // GATEWAY 的默认出边设为网关 defaultFlow
                if (Boolean.TRUE.equals(e.getIsDefault())
                        && fromNode != null && "GATEWAY".equals(fromNode.getNodeType())) {
                    FlowableGatewayDefault.apply(process, from, sf.getId());
                }
            }
            // 分支「输出名称」→ SequenceFlow.name，带入已部署 BPMN 供经办走向选择/反显
            if (StringUtils.hasText(e.getOutputName())) {
                sf.setName(e.getOutputName());
            }
        }

        return model;
    }

    /**
     * 构建审批 UserTask：挂任务分派监听器；会签模式补多实例特性 + 集合解析监听器。
     */
    private UserTask buildUserTask(FlowNodeDTO n) {
        UserTask ut = new UserTask();
        ut.setId(n.getNodeKey());
        ut.setName(n.getName());

        // 任务创建时解析候选人（与静态 BPMN 一致）
        ut.getTaskListeners().add(
                delegateTaskListener("create", "${taskAssignmentListener}"));

        if ("ALL".equalsIgnoreCase(n.getApproveMode())) {
            // 会签：多实例并行，集合由运行时解析器注入到 approverEmpIds 变量
            MultiInstanceLoopCharacteristics mi = new MultiInstanceLoopCharacteristics();
            mi.setSequential(false);
            mi.setInputDataItem(MI_COLLECTION);
            mi.setCollectionString(MI_COLLECTION);
            mi.setElementVariable(MI_ELEMENT_VAR);
            // 任一审批人驳回 或 全部完成 即结束多实例
            mi.setCompletionCondition(
                    "${rejected == true || nrOfCompletedInstances >= nrOfInstances}");
            ut.setLoopCharacteristics(mi);
            // 每个实例的受理人 = 当前遍历到的审批人
            ut.setAssignee("${" + MI_ELEMENT_VAR + "}");
            // 进入节点时由解析器把审批人集合写入 approverEmpIds（Task 7 提供实现）
            ut.getExecutionListeners().add(
                    delegateListener("start", "${multiInstanceApproverResolver}"));
        } else if ("GROUP_ALL".equalsIgnoreCase(n.getApproveMode())) {
            // 机构间全部审批、机构内任一负责人审批：顺序多实例每次只创建一个机构任务。
            // 当前组不设 assignee，由 TaskAssignmentListener 写入本组候选用户，任一人完成即可。
            MultiInstanceLoopCharacteristics mi = new MultiInstanceLoopCharacteristics();
            mi.setSequential(true);
            mi.setInputDataItem(MI_GROUP_COLLECTION);
            mi.setCollectionString(MI_GROUP_COLLECTION);
            mi.setElementVariable(MI_GROUP_ELEMENT_VAR);
            mi.setCompletionCondition(
                    "${rejected == true || nrOfCompletedInstances >= nrOfInstances}");
            ut.setLoopCharacteristics(mi);
            ut.getExecutionListeners().add(
                    delegateListener("start", "${multiInstanceApproverGroupResolver}"));
        }
        return ut;
    }

    /** 设置元素 id / name 通用方法 */
    private <T extends org.flowable.bpmn.model.FlowElement> T named(T el, FlowNodeDTO n) {
        el.setId(n.getNodeKey());
        el.setName(n.getName());
        return el;
    }

    /** 生成审批结果网关 id */
    private String approveGwId(String nodeKey) {
        return nodeKey + "_approveGw";
    }

    /**
     * 生成一条带唯一 id 的 SequenceFlow。
     *
     * @param seq   自增序号源，保证 id 唯一
     * @param from  源节点 id
     * @param to    目标节点 id
     * @param cond  条件表达式（含 ${}），null/空表示无条件
     */
    private SequenceFlow sequenceFlow(AtomicInteger seq, String from, String to, String cond) {
        SequenceFlow sf = new SequenceFlow();
        sf.setId("flow_" + seq.incrementAndGet());
        sf.setSourceRef(from);
        sf.setTargetRef(to);
        if (StringUtils.hasText(cond)) {
            sf.setConditionExpression(cond);
        }
        return sf;
    }

    /**
     * 把原图业务条件与 approved==true 组合：
     * <ul>
     *   <li>原边无条件 → {@code ${approved == true}}；</li>
     *   <li>原边有条件（形如 {@code ${X}}）→ {@code ${approved == true && (X)}}。</li>
     * </ul>
     */
    private String combineApprovedTrue(String rawCond) {
        if (!StringUtils.hasText(rawCond)) {
            return "${approved == true}";
        }
        // 去掉外层 ${ ... } 再与 approved==true 用 && 组合
        String inner = rawCond.trim();
        if (inner.startsWith("${") && inner.endsWith("}")) {
            inner = inner.substring(2, inner.length() - 1);
        }
        return "${approved == true && (" + inner + ")}";
    }

    /** 构造一个流程级 ExecutionListener（delegateExpression 形态） */
    private FlowableListener delegateListener(String event, String delegateExpr) {
        FlowableListener l = new FlowableListener();
        l.setEvent(event);
        l.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        l.setImplementation(delegateExpr);
        return l;
    }

    /** 构造一个任务级 TaskListener（delegateExpression 形态） */
    private FlowableListener delegateTaskListener(String event, String delegateExpr) {
        // TaskListener 与 ExecutionListener 在模型层同为 FlowableListener
        return delegateListener(event, delegateExpr);
    }

    /**
     * 网关默认流设置辅助：找到 process 中对应 id 的 ExclusiveGateway，设置 defaultFlow。
     * 抽成独立类型仅为语义聚焦，无外部依赖。
     */
    private static final class FlowableGatewayDefault {
        private static void apply(Process process, String gatewayId, String defaultFlowId) {
            org.flowable.bpmn.model.FlowElement fe = process.getFlowElement(gatewayId);
            if (fe instanceof ExclusiveGateway) {
                ((ExclusiveGateway) fe).setDefaultFlow(defaultFlowId);
            }
        }
    }
}
