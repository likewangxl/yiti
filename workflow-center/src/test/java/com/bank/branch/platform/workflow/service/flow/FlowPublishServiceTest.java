package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FlowPublishService} 单元测试（全 mock，不连库、不连 Flowable）。
 * <p>
 * 覆盖两条核心路径：
 * <ul>
 *   <li>校验未通过：抛 BizException，且绝不触碰 RepositoryService、不更新流程定义；</li>
 *   <li>校验通过：部署 + 写候选配置（先删后插）+ 版本自增到 PUBLISHED。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FlowPublishServiceTest {

    @Mock
    private FlowDefService flowDefService;
    @Mock
    private WfFlowDefMapper flowDefMapper;
    @Mock
    private FlowValidator flowValidator;
    @Mock
    private FlowBpmnGenerator bpmnGenerator;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private NodeCandidateConfMapper nodeCandidateConfMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FlowPublishService newService() {
        return new FlowPublishService(flowDefService, flowDefMapper, flowValidator,
                bpmnGenerator, repositoryService, nodeCandidateConfMapper, objectMapper);
    }

    /** 构造一个合法的最小流程图：start → a1(ANY, ROLE:CORP_DEPT) → end */
    private FlowGraphDTO sampleGraph() {
        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("测试流程");
        graph.setBizType("ALLOC_ADJUST");

        FlowNodeDTO start = new FlowNodeDTO();
        start.setNodeKey("start");
        start.setNodeType("START");
        start.setName("开始");

        FlowNodeDTO a1 = new FlowNodeDTO();
        a1.setNodeKey("a1");
        a1.setNodeType("APPROVAL");
        a1.setName("一级审批");
        a1.setApproveMode("ANY");
        FlowApproverDTO ap = new FlowApproverDTO();
        ap.setApproverType("ROLE");
        ap.setApproverValue("CORP_DEPT");
        a1.setApprovers(List.of(ap));

        FlowNodeDTO end = new FlowNodeDTO();
        end.setNodeKey("end");
        end.setNodeType("END");
        end.setName("结束");

        FlowEdgeDTO e1 = new FlowEdgeDTO();
        e1.setFromNodeKey("start");
        e1.setToNodeKey("a1");
        FlowEdgeDTO e2 = new FlowEdgeDTO();
        e2.setFromNodeKey("a1");
        e2.setToNodeKey("end");

        graph.setNodes(List.of(start, a1, end));
        graph.setEdges(List.of(e1, e2));
        return graph;
    }

    private WfFlowDef sampleDef() {
        WfFlowDef def = new WfFlowDef();
        def.setId("FD1");
        def.setFlowKey("ALLOC_ADJUST_abcd1234");
        def.setBizType("ALLOC_ADJUST");
        def.setName("测试流程");
        def.setStatus("DRAFT");
        def.setVersion(3);
        return def;
    }

    @Test
    void publish_invalid_throws_noDeploy() {
        FlowGraphDTO graph = sampleGraph();
        WfFlowDef def = sampleDef();
        when(flowDefService.getGraph("FD1")).thenReturn(graph);
        when(flowDefMapper.selectById("FD1")).thenReturn(def);
        // 校验失败：返回 1 条错误
        when(flowValidator.validate(any(), eq("ALLOC_ADJUST")))
                .thenReturn(new FlowValidator.ValidationResult(List.of("流程图缺少 START 节点")));

        FlowPublishService service = newService();

        assertThatThrownBy(() -> service.publish("FD1", "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("流程图缺少 START 节点");

        // 校验未通过：绝不部署、绝不更新流程定义、绝不动候选配置
        verifyNoInteractions(repositoryService);
        verifyNoInteractions(bpmnGenerator);
        verify(flowDefMapper, never()).updateById(any(WfFlowDef.class));
        verify(nodeCandidateConfMapper, never()).insert(any(WfNodeCandidateConf.class));
        verify(nodeCandidateConfMapper, never()).deleteByProcessDefinitionKey(anyString());
    }

    @Test
    void publish_valid_deploysAndBumpsVersion() {
        FlowGraphDTO graph = sampleGraph();
        WfFlowDef def = sampleDef();
        when(flowDefService.getGraph("FD1")).thenReturn(graph);
        when(flowDefMapper.selectById("FD1")).thenReturn(def);
        when(flowValidator.validate(any(), eq("ALLOC_ADJUST")))
                .thenReturn(new FlowValidator.ValidationResult(List.of()));

        // 生成一个非空 BpmnModel（含一个 process，BpmnXMLConverter 才能序列化出合法 XML）
        BpmnModel model = new BpmnModel();
        Process p = new Process();
        p.setId("DSN_ALLOC_ADJUST_abcd1234");
        p.setExecutable(true);
        model.addProcess(p);
        when(bpmnGenerator.generate(any(), eq("DSN_ALLOC_ADJUST_abcd1234"))).thenReturn(model);

        // mock 部署链
        DeploymentBuilder builder = org.mockito.Mockito.mock(DeploymentBuilder.class);
        Deployment deployment = org.mockito.Mockito.mock(Deployment.class);
        when(repositoryService.createDeployment()).thenReturn(builder);
        when(builder.name(anyString())).thenReturn(builder);
        when(builder.addBytes(anyString(), any())).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("DEP1");

        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.deploymentId("DEP1")).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        lenient().when(pd.getId()).thenReturn("DSN_ALLOC_ADJUST_abcd1234:1:xyz");
        lenient().when(pd.getKey()).thenReturn("DSN_ALLOC_ADJUST_abcd1234");

        FlowPublishService service = newService();
        service.publish("FD1", "OP1");

        // 部署链被调用
        verify(repositoryService).createDeployment();
        verify(builder).deploy();

        // 候选配置先删后插：deleteByProcessDefinitionKey(影子KEY) + 至少 1 次 insert
        verify(nodeCandidateConfMapper).deleteByProcessDefinitionKey("DSN_ALLOC_ADJUST_abcd1234");
        ArgumentCaptor<WfNodeCandidateConf> confCaptor = ArgumentCaptor.forClass(WfNodeCandidateConf.class);
        verify(nodeCandidateConfMapper, atLeastOnce()).insert(confCaptor.capture());
        WfNodeCandidateConf conf = confCaptor.getValue();
        assertThat(conf.getProcessDefinitionKey()).isEqualTo("DSN_ALLOC_ADJUST_abcd1234");
        assertThat(conf.getNodeKey()).isEqualTo("a1");
        assertThat(conf.getCandidateType()).isEqualTo("ROLE");
        assertThat(conf.getCandidateValue()).isEqualTo("[\"CORP_DEPT\"]");

        // 流程定义被更新：status=PUBLISHED && version=旧+1 && 回填部署坐标
        ArgumentCaptor<WfFlowDef> defCaptor = ArgumentCaptor.forClass(WfFlowDef.class);
        verify(flowDefMapper, times(1)).updateById(defCaptor.capture());
        WfFlowDef saved = defCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PUBLISHED");
        assertThat(saved.getVersion()).isEqualTo(4); // 旧 3 + 1
        assertThat(saved.getDeployedProcDefKey()).isEqualTo("DSN_ALLOC_ADJUST_abcd1234");
        assertThat(saved.getDeployedProcDefId()).isEqualTo("DSN_ALLOC_ADJUST_abcd1234:1:xyz");
        assertThat(saved.getUpdatedBy()).isEqualTo("OP1");
    }

    @Test
    void publish_defNotFound_throws() {
        when(flowDefService.getGraph("FDX")).thenReturn(sampleGraph());
        when(flowDefMapper.selectById("FDX")).thenReturn(null);

        FlowPublishService service = newService();

        assertThatThrownBy(() -> service.publish("FDX", "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("流程定义不存在");

        verifyNoInteractions(repositoryService);
    }
}
