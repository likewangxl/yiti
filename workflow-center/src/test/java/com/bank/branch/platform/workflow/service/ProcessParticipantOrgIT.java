package com.bank.branch.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.governance.api.CalendarApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfFlowNode;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowEdgeMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowNodeApproverMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowNodeMapper;
import com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper;
import com.bank.branch.platform.workflow.service.flow.FlowDefService;
import com.bank.branch.platform.workflow.service.flow.FlowPublishService;
import com.bank.branch.platform.workflow.support.WfFlowableTestApp;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 参与机构（{@code WF_PROCESS_ORG}）写入挂点集成测试。
 * <p>
 * 真部署 + 真启动一条最小审批流（{@link FlowDefService#create} + {@link FlowPublishService#publish}，
 * 与 {@code FlowPublishServiceIT} 同一套手法），再走真实的 {@link ProcessStartService#startProcess}
 * 与 {@link TaskOperationService#claimTask} 主链路，断言：
 * <ul>
 *   <li>流程发起后 {@code WF_PROCESS_ORG} 含发起人主机构（source=START，挂点见 {@code ProcessStartService}）；</li>
 *   <li>签收后追加办理人主机构（source=CLAIM，挂点见 {@code TaskOperationService#claimTask}）。</li>
 * </ul>
 * 首个审批节点仅配候选组角色（无 BPMN 静态 assignee），验证 D5 口径：
 * 候选组任务创建时不写入 {@code TaskAssignmentListener} 的 ASSIGN 记录，只有签收（CLAIM）才落一条参与机构。
 * </p>
 */
@SpringBootTest(classes = WfFlowableTestApp.class)
@ActiveProfiles("test")
class ProcessParticipantOrgIT {

    private static final Logger log = LoggerFactory.getLogger(ProcessParticipantOrgIT.class);

    @Autowired
    private FlowDefService flowDefService;
    @Autowired
    private FlowPublishService flowPublishService;
    @Autowired
    private WfFlowDefMapper flowDefMapper;
    @Autowired
    private WfFlowNodeMapper flowNodeMapper;
    @Autowired
    private WfFlowEdgeMapper flowEdgeMapper;
    @Autowired
    private WfFlowNodeApproverMapper flowNodeApproverMapper;
    @Autowired
    private NodeCandidateConfMapper nodeCandidateConfMapper;
    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ProcessStartService processStartService;
    @Autowired
    private TaskOperationService taskOperationService;
    @Autowired
    private WfProcessOrgMapper wfProcessOrgMapper;
    @Autowired
    private BizProcessMapMapper bizProcessMapMapper;

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

    /** 本用例创建的 flowDefId / flowKey / processInstanceId，@AfterEach 据此清理。 */
    private String currentFlowDefId;
    private String currentFlowKey;
    private String currentPi;

    /**
     * 清理本用例写入的数据：WF_PROCESS_ORG → BIZ_PROCESS_MAP → WF_FLOW_NODE_APPROVER →
     * WF_FLOW_NODE → WF_FLOW_EDGE → WF_FLOW_DEF → WF_NODE_CANDIDATE_CONF（影子 KEY）→
     * Flowable 部署（cascade，连带 ACT_* 运行时/历史数据）。手法与 FlowPublishServiceIT 一致。
     */
    @AfterEach
    void cleanupItData() {
        String pi = currentPi;
        currentPi = null;
        if (pi != null) {
            try {
                wfProcessOrgMapper.delete(new LambdaQueryWrapper<WfProcessOrg>()
                        .eq(WfProcessOrg::getProcessInstanceId, pi));
            } catch (Exception e) {
                log.warn("[IT cleanup] 删除 WF_PROCESS_ORG 失败，pi={}", pi, e);
            }
            try {
                bizProcessMapMapper.delete(new LambdaQueryWrapper<BizProcessMap>()
                        .eq(BizProcessMap::getProcessInstanceId, pi));
            } catch (Exception e) {
                log.warn("[IT cleanup] 删除 BIZ_PROCESS_MAP 失败，pi={}", pi, e);
            }
        }

        String flowDefId = currentFlowDefId;
        String flowKey = currentFlowKey;
        currentFlowDefId = null;
        currentFlowKey = null;
        if (flowDefId == null) {
            return;
        }

        try {
            List<WfFlowNode> nodes = flowNodeMapper.selectByFlowDefId(flowDefId);
            if (!nodes.isEmpty()) {
                List<String> nodeIds = nodes.stream().map(WfFlowNode::getId).toList();
                flowNodeApproverMapper.deleteByNodeIds(nodeIds);
            }
        } catch (Exception e) {
            log.warn("[IT cleanup] 删除 WF_FLOW_NODE_APPROVER 失败，flowDefId={}", flowDefId, e);
        }
        try {
            flowNodeMapper.deleteByFlowDefId(flowDefId);
        } catch (Exception e) {
            log.warn("[IT cleanup] 删除 WF_FLOW_NODE 失败，flowDefId={}", flowDefId, e);
        }
        try {
            flowEdgeMapper.deleteByFlowDefId(flowDefId);
        } catch (Exception e) {
            log.warn("[IT cleanup] 删除 WF_FLOW_EDGE 失败，flowDefId={}", flowDefId, e);
        }
        try {
            flowDefMapper.deleteById(flowDefId);
        } catch (Exception e) {
            log.warn("[IT cleanup] 删除 WF_FLOW_DEF 失败，flowDefId={}", flowDefId, e);
        }

        if (flowKey == null) {
            return;
        }
        String shadowKey = "DSN_" + flowKey;
        try {
            nodeCandidateConfMapper.deleteByProcessDefinitionKey(shadowKey);
        } catch (Exception e) {
            log.warn("[IT cleanup] 删除 WF_NODE_CANDIDATE_CONF 失败，shadowKey={}", shadowKey, e);
        }
        try {
            List<Deployment> deployments = repositoryService.createDeploymentQuery()
                    .processDefinitionKey(shadowKey)
                    .list();
            for (Deployment dep : deployments) {
                repositoryService.deleteDeployment(dep.getId(), true);
            }
        } catch (Exception e) {
            log.warn("[IT cleanup] 删除 Flowable 部署失败，shadowKey={}", shadowKey, e);
        }
    }

    private String uniqueKey(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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

    /** 落库一份草稿并把它的 flowKey 改成指定唯一值，供影子 KEY 推断；记录用于清理的坐标。 */
    private WfFlowDef createDefWithKey(FlowGraphDTO graph, String flowKey) {
        String flowDefId = flowDefService.create(graph, "IT_OP");
        WfFlowDef def = flowDefMapper.selectById(flowDefId);
        def.setFlowKey(flowKey);
        flowDefMapper.updateById(def);
        currentFlowDefId = flowDefId;
        currentFlowKey = flowKey;
        return flowDefMapper.selectById(flowDefId);
    }

    /**
     * 发起流程 → 断言参与机构含发起人机构（START）；签收 → 断言追加办理人机构（CLAIM）。
     * <p>首节点仅配候选组角色，无静态 assignee，因此 {@code TaskAssignmentListener} 的 ASSIGN
     * 挂点在任务创建时不触发写入（D5：仅候选组不计入），CLAIM 记录完全来自
     * {@code TaskOperationService#claimTask}。</p>
     */
    @Test
    void startAndClaim_recordsParticipantOrgs() {
        OrgDTO startOrg = new OrgDTO();
        startOrg.setOrgCode("ORG_START");
        when(orgApi.getUserMainOrg("E_START")).thenReturn(startOrg);

        OrgDTO handlerOrg = new OrgDTO();
        handlerOrg.setOrgCode("ORG_H");
        when(orgApi.getUserMainOrg("E_HANDLER")).thenReturn(handlerOrg);

        // resolveStartOrgLevel 会额外查 orgApi.getOrg(startOrgId)，与本用例断言无关，宽松兜底 null
        lenient().when(orgApi.getOrg(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("IT-参与机构写入");
        graph.setBizType("ALLOC_ADJUST");
        graph.setNodes(List.of(
                node("start", "START", "开始"),
                approvalNode("a1", "审批", "ANY", "IT_PARTICIPANT_ROLE"),
                node("end", "END", "结束")));
        graph.setEdges(List.of(edge("start", "a1"), edge("a1", "end")));

        String flowKey = uniqueKey("IT_ORG");
        WfFlowDef def = createDefWithKey(graph, flowKey);
        flowPublishService.publish(def.getId(), "IT_OP");
        String shadowKey = "DSN_" + flowKey;

        String bizId = "PO" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("ALLOC_ADJUST");
        cmd.setBizId(bizId);
        cmd.setBusinessKey("ALLOC_ADJUST:" + bizId);
        cmd.setProcessDefinitionKey(shadowKey);
        cmd.setStartUser("E_START");
        cmd.setStartOrgId("IT_ORG_CODE");
        cmd.setTitle("参与机构写入挂点IT");
        cmd.setVariables(Map.of());

        WorkflowLaunchResp resp = processStartService.startProcess(cmd);
        String pi = resp.getProcessInstanceId();
        currentPi = pi;

        // 发起后：参与机构含发起人主机构 ORG_START
        assertThat(wfProcessOrgMapper.selectOrgCodesByPi(pi)).contains("ORG_START");

        when(currentUserApi.getCurrentEmpId()).thenReturn("E_HANDLER");
        String taskId = resp.getFirstTaskId();
        assertThat(taskId).as("首个审批节点应产生候选组任务").isNotBlank();

        taskOperationService.claimTask(taskId);

        // 签收后：追加办理人主机构 ORG_H，发起人机构仍在
        assertThat(wfProcessOrgMapper.selectOrgCodesByPi(pi)).contains("ORG_START", "ORG_H");
    }
}
