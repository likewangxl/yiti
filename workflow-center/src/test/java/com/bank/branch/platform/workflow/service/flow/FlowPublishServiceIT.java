package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.CalendarApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.bank.branch.platform.workflow.support.WfFlowableTestApp;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * {@link FlowPublishService} 真部署集成测试。
 * <p>
 * 连真实 yiti 库 + 真实 Flowable 引擎：用 {@link FlowDefService#create} 落库一份流程图草稿，
 * 调 {@link FlowPublishService#publish} 把它编译成 BPMN 并部署到 Flowable 影子 KEY，
 * 然后用 RuntimeService / TaskService 启动实例并跑通流转，断言流程能正常结束 / 终止 / 分支路由。
 * </p>
 *
 * <p>跨模块 API（{@link UserApi} 等）用 {@code @MockBean} 提供：审批人解析靠
 * {@code UserApi.getEmpIdsByRoleCode}，本测试 mock 它返回固定 empId。</p>
 *
 * <p>隔离策略：每个用例用唯一 flowKey（带随机后缀），影子 KEY 因此唯一，
 * 不与历史 / 并发部署冲突；结束后不强制清理。</p>
 */
@SpringBootTest(classes = WfFlowableTestApp.class)
@ActiveProfiles("test")
class FlowPublishServiceIT {

    @Autowired
    private FlowDefService flowDefService;
    @Autowired
    private FlowPublishService flowPublishService;
    @Autowired
    private WfFlowDefMapper flowDefMapper;

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;

    // ---- 跨模块协作者 mock（auth / governance，扫描不到真实现） ----
    @MockBean
    private UserApi userApi;
    @MockBean
    private OrgApi orgApi;
    @MockBean
    private CurrentUserApi currentUserApi;
    @MockBean
    private NotifyApi notifyApi;
    @MockBean
    private CalendarApi calendarApi;

    /** 生成唯一 flowKey 前缀，避免影子 KEY 冲突 */
    private String uniqueKey(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 落库一份草稿并把它的 flowKey 改成指定唯一值（FlowDefService.create 内部随机生成
     * flowKey，这里覆盖成可预期的唯一 key，便于推断影子 KEY = DSN_ + flowKey）。
     */
    private WfFlowDef createDefWithKey(FlowGraphDTO graph, String flowKey) {
        String flowDefId = flowDefService.create(graph, "IT_OP");
        WfFlowDef def = flowDefMapper.selectById(flowDefId);
        def.setFlowKey(flowKey);
        flowDefMapper.updateById(def);
        return flowDefMapper.selectById(flowDefId);
    }

    private FlowNodeDTO node(String key, String type, String name) {
        FlowNodeDTO n = new FlowNodeDTO();
        n.setNodeKey(key);
        n.setNodeType(type);
        n.setName(name);
        return n;
    }

    private FlowNodeDTO approvalNode(String key, String name, String mode, String roleCode) {
        FlowNodeDTO n = node(key, "APPROVAL", name);
        n.setApproveMode(mode);
        FlowApproverDTO ap = new FlowApproverDTO();
        ap.setApproverType("ROLE");
        ap.setApproverValue(roleCode);
        n.setApprovers(List.of(ap));
        return n;
    }

    private FlowEdgeDTO edge(String from, String to) {
        FlowEdgeDTO e = new FlowEdgeDTO();
        e.setFromNodeKey(from);
        e.setToNodeKey(to);
        return e;
    }

    /** 用例 1：或签审批通过 → 流程正常结束 */
    @Test
    void publishedFlow_runsAnyApprove_toEnd() {
        lenient().when(userApi.getEmpIdsByRoleCode("CORP_DEPT")).thenReturn(List.of("E001"));

        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("IT-或签通过");
        graph.setBizType("ALLOC_ADJUST");
        graph.setNodes(List.of(
                node("start", "START", "开始"),
                approvalNode("a1", "审批", "ANY", "CORP_DEPT"),
                node("end", "END", "结束")));
        graph.setEdges(List.of(edge("start", "a1"), edge("a1", "end")));

        String flowKey = uniqueKey("IT_PUB_ANY");
        WfFlowDef def = createDefWithKey(graph, flowKey);
        flowPublishService.publish(def.getId(), "IT_OP");

        String shadowKey = "DSN_" + flowKey;
        Map<String, Object> vars = new HashMap<>();
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(shadowKey, vars);
        assertThat(pi).isNotNull();

        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("a1").singleResult();
        assertThat(task).as("应进入审批节点 a1").isNotNull();

        taskService.setVariable(task.getId(), "approved", true);
        taskService.complete(task.getId());

        assertThat(isFinished(pi.getId())).as("审批通过后流程应结束").isTrue();
    }

    /** 用例 2：审批驳回 → 流程走 rejectedEnd 终止 */
    @Test
    void publishedFlow_reject_terminates() {
        lenient().when(userApi.getEmpIdsByRoleCode("CORP_DEPT")).thenReturn(List.of("E001"));

        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("IT-驳回");
        graph.setBizType("ALLOC_ADJUST");
        graph.setNodes(List.of(
                node("start", "START", "开始"),
                approvalNode("a1", "审批", "ANY", "CORP_DEPT"),
                node("end", "END", "结束")));
        graph.setEdges(List.of(edge("start", "a1"), edge("a1", "end")));

        String flowKey = uniqueKey("IT_PUB_REJ");
        WfFlowDef def = createDefWithKey(graph, flowKey);
        flowPublishService.publish(def.getId(), "IT_OP");

        String shadowKey = "DSN_" + flowKey;
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(shadowKey, new HashMap<>());

        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("a1").singleResult();
        assertThat(task).isNotNull();

        taskService.setVariable(task.getId(), "approved", false);
        taskService.complete(task.getId());

        assertThat(isFinished(pi.getId())).as("驳回后流程应终止").isTrue();
    }

    /** 用例 3：网关按条件路由——bizKind=CORP 进 a1；bizKind=PER 走默认边到 end */
    @Test
    void publishedFlow_branch_routesByCondition() {
        lenient().when(userApi.getEmpIdsByRoleCode("CORP_DEPT")).thenReturn(List.of("E001"));

        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("IT-条件分支");
        graph.setBizType("ALLOC_ADJUST");
        graph.setNodes(List.of(
                node("start", "START", "开始"),
                node("gw", "GATEWAY", "分流网关"),
                approvalNode("a1", "对公审批", "ANY", "CORP_DEPT"),
                node("end", "END", "结束")));

        // gw → a1 条件 bizKind EQ CORP
        FlowEdgeDTO toA1 = edge("gw", "a1");
        FlowConditionDTO cond = new FlowConditionDTO();
        cond.setLogic("AND");
        cond.setConditions(List.of(new FlowConditionDTO.Cond("bizKind", "EQ", "CORP")));
        toA1.setCondition(cond);
        // gw → end 默认边
        FlowEdgeDTO toEndDefault = edge("gw", "end");
        toEndDefault.setIsDefault(true);

        graph.setEdges(List.of(edge("start", "gw"), toA1, toEndDefault, edge("a1", "end")));

        String flowKey = uniqueKey("IT_PUB_BR");
        WfFlowDef def = createDefWithKey(graph, flowKey);
        flowPublishService.publish(def.getId(), "IT_OP");

        String shadowKey = "DSN_" + flowKey;

        // CORP → 进入 a1
        Map<String, Object> corpVars = new HashMap<>();
        corpVars.put("bizKind", "CORP");
        ProcessInstance corpPi = runtimeService.startProcessInstanceByKey(shadowKey, corpVars);
        Task corpTask = taskService.createTaskQuery()
                .processInstanceId(corpPi.getId()).taskDefinitionKey("a1").singleResult();
        assertThat(corpTask).as("bizKind=CORP 应进入 a1 审批节点").isNotNull();

        // PER → 走默认边直达 end，不进 a1，实例直接结束
        Map<String, Object> perVars = new HashMap<>();
        perVars.put("bizKind", "PER");
        ProcessInstance perPi = runtimeService.startProcessInstanceByKey(shadowKey, perVars);
        Task perTask = taskService.createTaskQuery()
                .processInstanceId(perPi.getId()).taskDefinitionKey("a1").singleResult();
        assertThat(perTask).as("bizKind=PER 不应进入 a1").isNull();
        assertThat(isFinished(perPi.getId())).as("bizKind=PER 走默认边应直接结束").isTrue();
    }

    /**
     * 用例 4：会签（ALL）——任一审批人驳回，整单终止。
     * <p>mock 解析出 2 个 empId（E001/E002），其一 approved=false（带 rejected=true）应触发
     * MI 完成条件提前结束并走驳回路径终止流程。</p>
     */
    @Test
    void publishedFlow_allNode_anyRejectTerminates() {
        lenient().when(userApi.getEmpIdsByRoleCode("CORP_DEPT")).thenReturn(List.of("E001", "E002"));

        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("IT-会签驳回");
        graph.setBizType("ALLOC_ADJUST");
        graph.setNodes(List.of(
                node("start", "START", "开始"),
                approvalNode("a1", "会签审批", "ALL", "CORP_DEPT"),
                node("end", "END", "结束")));
        graph.setEdges(List.of(edge("start", "a1"), edge("a1", "end")));

        String flowKey = uniqueKey("IT_PUB_ALL");
        WfFlowDef def = createDefWithKey(graph, flowKey);
        flowPublishService.publish(def.getId(), "IT_OP");

        String shadowKey = "DSN_" + flowKey;
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(shadowKey, new HashMap<>());

        // 会签产生多个并行实例；取一个任务驳回（rejected=true + approved=false）
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("a1").list();
        assertThat(tasks).as("会签应产生至少 1 个任务").isNotEmpty();

        Task first = tasks.get(0);
        Map<String, Object> rejectVars = new HashMap<>();
        rejectVars.put("approved", false);
        rejectVars.put("rejected", true);
        taskService.complete(first.getId(), rejectVars);

        assertThat(isFinished(pi.getId())).as("会签任一驳回应终止整单").isTrue();
    }

    /** 流程实例是否已结束（运行时查不到 = 结束；再以历史确认） */
    private boolean isFinished(String processInstanceId) {
        long running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).count();
        if (running > 0) {
            return false;
        }
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        return hpi != null && hpi.getEndTime() != null;
    }
}
