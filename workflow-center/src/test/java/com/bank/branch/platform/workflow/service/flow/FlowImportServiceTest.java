package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowImportService 单元测试（Mockito）。
 * <p>
 * 用真实 {@link BpmnModel} 构造一个最简流程（start → a1 → end），
 * mock RepositoryService 查询链与候选配置 Mapper，验证反向导入出的 {@link FlowGraphDTO}
 * 结构正确，并验证幂等（已存在则不再调用 createImported）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FlowImportServiceTest {

    private static final String PROC_KEY = "perf_alloc_adjust_corp_v1";
    private static final String PROC_DEF_ID = "pd:34:x";

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private NodeCandidateConfMapper nodeCandidateConfMapper;
    @Mock
    private FlowDefService flowDefService;
    @Mock
    private WfFlowDefMapper flowDefMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FlowImportService service;

    @BeforeEach
    void setUp() {
        service = new FlowImportService(
                repositoryService, nodeCandidateConfMapper, flowDefService, flowDefMapper, objectMapper);
    }

    /** 构造一个最简 BpmnModel：start → a1(UserTask) → end */
    private BpmnModel simpleModel() {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId(PROC_KEY);
        process.setName("机构分配调整");

        StartEvent start = new StartEvent();
        start.setId("start");
        process.addFlowElement(start);

        UserTask a1 = new UserTask();
        a1.setId("a1");
        a1.setName("机构负责人审批");
        process.addFlowElement(a1);

        EndEvent end = new EndEvent();
        end.setId("end");
        process.addFlowElement(end);

        SequenceFlow f1 = new SequenceFlow();
        f1.setId("f1");
        f1.setSourceRef("start");
        f1.setTargetRef("a1");
        process.addFlowElement(f1);

        SequenceFlow f2 = new SequenceFlow();
        f2.setId("f2");
        f2.setSourceRef("a1");
        f2.setTargetRef("end");
        process.addFlowElement(f2);

        model.addProcess(process);
        return model;
    }

    /** mock RepositoryService 查询链返回 procDef */
    private void mockRepository(BpmnModel model) {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        ProcessDefinition procDef = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionKey(PROC_KEY)).thenReturn(query);
        when(query.latestVersion()).thenReturn(query);
        when(query.singleResult()).thenReturn(procDef);
        when(procDef.getId()).thenReturn(PROC_DEF_ID);
        lenient().when(procDef.getKey()).thenReturn(PROC_KEY);
        lenient().when(procDef.getName()).thenReturn("机构分配调整");
        when(repositoryService.getBpmnModel(PROC_DEF_ID)).thenReturn(model);
    }

    @Test
    @DisplayName("importFromDeployed：反向导入构造正确的只读 FlowGraphDTO")
    void importFromDeployed_buildsReadonlyGraph() throws Exception {
        when(flowDefMapper.selectByFlowKey("imported_" + PROC_KEY)).thenReturn(null);
        mockRepository(simpleModel());

        // a1 节点候选：ROLE / ["CORP_DEPT"]
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setProcessDefinitionKey(PROC_KEY);
        conf.setNodeKey("a1");
        conf.setCandidateType("ROLE");
        conf.setCandidateValue("[\"CORP_DEPT\"]");
        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey(PROC_KEY, "a1"))
                .thenReturn(List.of(conf));

        when(flowDefService.createImported(org.mockito.ArgumentMatchers.any(), eq(PROC_KEY)))
                .thenReturn("NEW-DEF-ID");

        String result = service.importFromDeployed(PROC_KEY);
        assertThat(result).isEqualTo("NEW-DEF-ID");

        ArgumentCaptor<FlowGraphDTO> captor = ArgumentCaptor.forClass(FlowGraphDTO.class);
        verify(flowDefService).createImported(captor.capture(), eq(PROC_KEY));
        FlowGraphDTO graph = captor.getValue();

        // bizType 推导
        assertThat(graph.getBizType()).isEqualTo("ALLOC_ADJUST");

        // 节点：start(START)/a1(APPROVAL)/end(END)
        FlowNodeDTO start = nodeByKey(graph, "start");
        assertThat(start.getNodeType()).isEqualTo("START");
        FlowNodeDTO end = nodeByKey(graph, "end");
        assertThat(end.getNodeType()).isEqualTo("END");

        FlowNodeDTO a1 = nodeByKey(graph, "a1");
        assertThat(a1.getNodeType()).isEqualTo("APPROVAL");
        assertThat(a1.getApprovers()).hasSize(1);
        FlowApproverDTO approver = a1.getApprovers().get(0);
        assertThat(approver.getApproverType()).isEqualTo("ROLE");
        assertThat(approver.getApproverValue()).isEqualTo("CORP_DEPT");

        // 边：2 条
        assertThat(graph.getEdges()).hasSize(2);
    }

    @Test
    @DisplayName("importFromDeployed：已存在同 flowKey 时幂等跳过，不调用 createImported")
    void importFromDeployed_idempotentSkip() {
        WfFlowDef existing = new WfFlowDef();
        existing.setId("EXIST-ID");
        existing.setFlowKey("imported_" + PROC_KEY);
        when(flowDefMapper.selectByFlowKey("imported_" + PROC_KEY)).thenReturn(existing);

        String result = service.importFromDeployed(PROC_KEY);

        assertThat(result).isEqualTo("EXIST-ID");
        verify(flowDefService, never())
                .createImported(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("importFromDeployed：顺序机构组多实例反向识别为 GROUP_ALL")
    void importFromDeployed_preservesGroupAllMode() {
        when(flowDefMapper.selectByFlowKey("imported_" + PROC_KEY)).thenReturn(null);
        BpmnModel model = simpleModel();
        UserTask task = (UserTask) model.getMainProcess().getFlowElement("a1");
        MultiInstanceLoopCharacteristics mi = new MultiInstanceLoopCharacteristics();
        mi.setSequential(true);
        mi.setCollectionString("approverGroups");
        mi.setElementVariable("approverGroup");
        task.setLoopCharacteristics(mi);
        FlowableListener listener = new FlowableListener();
        listener.setEvent("start");
        listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        listener.setImplementation("${multiInstanceApproverGroupResolver}");
        task.getExecutionListeners().add(listener);
        mockRepository(model);

        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setProcessDefinitionKey(PROC_KEY);
        conf.setNodeKey("a1");
        conf.setCandidateType("VAR");
        conf.setCandidateValue("[\"originalOwnerOrgApprovalGroups\"]");
        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey(PROC_KEY, "a1"))
                .thenReturn(List.of(conf));
        when(flowDefService.createImported(org.mockito.ArgumentMatchers.any(), eq(PROC_KEY)))
                .thenReturn("GROUP-DEF-ID");

        service.importFromDeployed(PROC_KEY);

        ArgumentCaptor<FlowGraphDTO> captor = ArgumentCaptor.forClass(FlowGraphDTO.class);
        verify(flowDefService).createImported(captor.capture(), eq(PROC_KEY));
        assertThat(nodeByKey(captor.getValue(), "a1").getApproveMode()).isEqualTo("GROUP_ALL");
    }

    private FlowNodeDTO nodeByKey(FlowGraphDTO graph, String key) {
        return graph.getNodes().stream()
                .filter(n -> key.equals(n.getNodeKey()))
                .findFirst().orElseThrow();
    }
}
