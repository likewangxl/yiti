package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfFlowEdge;
import com.bank.branch.platform.workflow.entity.WfFlowNode;
import com.bank.branch.platform.workflow.entity.WfFlowNodeApprover;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowEdgeMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowNodeApproverMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowNodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * FlowDefService 单元测试（Mockito，mock 4 个 Mapper）。
 */
@ExtendWith(MockitoExtension.class)
class FlowDefServiceTest {

    @Mock
    private WfFlowDefMapper flowDefMapper;
    @Mock
    private WfFlowNodeMapper nodeMapper;
    @Mock
    private WfFlowNodeApproverMapper approverMapper;
    @Mock
    private WfFlowEdgeMapper edgeMapper;

    /** 使用真实 ObjectMapper，验证 JSON 序列化/反序列化路径 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private FlowDefService service;

    @BeforeEach
    void setUp() {
        service = new FlowDefService(flowDefMapper, nodeMapper, approverMapper, edgeMapper, objectMapper);
    }

    // ------------------------------------------------------------------ //
    //  1. saveGraph_replacesNodesEdgesApprovers                           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("saveGraph：整图替换——删旧数据再插新数据")
    void saveGraph_replacesNodesEdgesApprovers() {
        // --- 准备 def（DRAFT，非只读）
        WfFlowDef def = draftDef("DEF-001");
        when(flowDefMapper.selectById("DEF-001")).thenReturn(def);

        // --- 准备旧 node（供 deleteByNodeIds 使用）
        WfFlowNode oldNode = new WfFlowNode();
        oldNode.setId("OLD-N1");
        oldNode.setFlowDefId("DEF-001");
        when(nodeMapper.selectByFlowDefId("DEF-001")).thenReturn(List.of(oldNode));

        // --- 构造新图：2 节点 + 1 条边 + 1 个 approver
        FlowGraphDTO graph = buildSimpleGraph();

        // --- 执行
        service.saveGraph("DEF-001", graph, "user01");

        // --- 验证：删除旧 approvers
        verify(approverMapper).deleteByNodeIds(List.of("OLD-N1"));
        // --- 验证：删除旧 edges
        verify(edgeMapper).deleteByFlowDefId("DEF-001");
        // --- 验证：删除旧 nodes
        verify(nodeMapper).deleteByFlowDefId("DEF-001");

        // --- 验证：插入新 nodes（≥2 次）；用 ArgumentMatchers.<T>any() 消歧 MyBatis-Plus BaseMapper#insert 重载
        verify(nodeMapper, atLeast(2)).insert(ArgumentMatchers.<WfFlowNode>any(WfFlowNode.class));
        // --- 验证：插入新 edges（≥1 次）
        verify(edgeMapper, atLeast(1)).insert(ArgumentMatchers.<WfFlowEdge>any(WfFlowEdge.class));
        // --- 验证：插入新 approvers（≥1 次）
        verify(approverMapper, atLeast(1)).insert(ArgumentMatchers.<WfFlowNodeApprover>any(WfFlowNodeApprover.class));

        // --- 验证：更新 def
        verify(flowDefMapper).updateById(any(WfFlowDef.class));
    }

    // ------------------------------------------------------------------ //
    //  2. saveGraph_readonlyImport_throws                                 //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("saveGraph：只读导入的流程定义不允许修改")
    void saveGraph_readonlyImport_throws() {
        WfFlowDef def = draftDef("DEF-002");
        def.setIsReadonlyImport(1);
        when(flowDefMapper.selectById("DEF-002")).thenReturn(def);

        FlowGraphDTO graph = buildSimpleGraph();

        assertThatThrownBy(() -> service.saveGraph("DEF-002", graph, "user01"))
                .isInstanceOf(BizException.class);

        // 不应有任何写操作
        verify(nodeMapper, never()).deleteByFlowDefId(any());
        verify(edgeMapper, never()).deleteByFlowDefId(any());
        verify(approverMapper, never()).deleteByNodeIds(anyList());
        verify(nodeMapper, never()).insert(ArgumentMatchers.<WfFlowNode>any(WfFlowNode.class));
        verify(edgeMapper, never()).insert(ArgumentMatchers.<WfFlowEdge>any(WfFlowEdge.class));
        verify(approverMapper, never()).insert(ArgumentMatchers.<WfFlowNodeApprover>any(WfFlowNodeApprover.class));
    }

    // ------------------------------------------------------------------ //
    //  3. getGraph_assemblesByNodeKey                                     //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("getGraph：edge 的 fromNodeKey/toNodeKey 通过 nodeId→nodeKey 正确映射")
    void getGraph_assemblesByNodeKey() throws Exception {
        WfFlowDef def = draftDef("DEF-003");
        when(flowDefMapper.selectById("DEF-003")).thenReturn(def);

        // 节点 N1(nodeKey=start)，N2(nodeKey=a1)
        WfFlowNode n1 = node("N1", "DEF-003", "start");
        WfFlowNode n2 = node("N2", "DEF-003", "a1");
        when(nodeMapper.selectByFlowDefId("DEF-003")).thenReturn(List.of(n1, n2));

        // 边：fromNodeId=N1, toNodeId=N2
        WfFlowEdge edge = new WfFlowEdge();
        edge.setId("E1");
        edge.setFlowDefId("DEF-003");
        edge.setFromNodeId("N1");
        edge.setToNodeId("N2");
        edge.setIsDefault(0);
        edge.setConditionJson(null);
        when(edgeMapper.selectByFlowDefId("DEF-003")).thenReturn(List.of(edge));

        // approver 挂在 N2 节点
        WfFlowNodeApprover approver = new WfFlowNodeApprover();
        approver.setId("A1");
        approver.setNodeId("N2");
        approver.setApproverType("ROLE");
        approver.setApproverValue("ROLE_MGR");
        when(approverMapper.selectByNodeIds(List.of("N1", "N2"))).thenReturn(List.of(approver));

        // --- 执行
        FlowGraphDTO result = service.getGraph("DEF-003");

        // --- 断言：edge 的 nodeKey 已映射正确
        assertThat(result.getEdges()).hasSize(1);
        FlowEdgeDTO edgeDTO = result.getEdges().get(0);
        assertThat(edgeDTO.getFromNodeKey()).isEqualTo("start");
        assertThat(edgeDTO.getToNodeKey()).isEqualTo("a1");

        // --- 断言：a1 节点的 approvers 非空
        FlowNodeDTO a1Node = result.getNodes().stream()
                .filter(n -> "a1".equals(n.getNodeKey()))
                .findFirst().orElseThrow();
        assertThat(a1Node.getApprovers()).isNotEmpty();
        assertThat(a1Node.getApprovers().get(0).getApproverType()).isEqualTo("ROLE");
    }

    // ------------------------------------------------------------------ //
    //  4. deleteDraft_published_throws                                    //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("deleteDraft：已发布流程不允许删除")
    void deleteDraft_published_throws() {
        WfFlowDef def = draftDef("DEF-004");
        def.setStatus("PUBLISHED");
        def.setIsReadonlyImport(0);
        when(flowDefMapper.selectById("DEF-004")).thenReturn(def);

        assertThatThrownBy(() -> service.deleteDraft("DEF-004"))
                .isInstanceOf(BizException.class);
    }

    // ------------------------------------------------------------------ //
    //  5. listAll_mapsToDto                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("listAll：将 WfFlowDef 列表正确映射为 FlowDefDTO 列表")
    void listAll_mapsToDto() {
        WfFlowDef def = draftDef("DEF-005");
        def.setName("测试流程");
        def.setBizType("TARGET_ADJUST");
        def.setVersion(2);
        def.setDeployedProcDefKey("key-123");
        def.setIsReadonlyImport(0);
        def.setUpdatedTime(LocalDateTime.of(2024, 1, 1, 10, 0));

        when(flowDefMapper.selectList(null)).thenReturn(List.of(def));

        var result = service.listAll();

        assertThat(result).hasSize(1);
        var dto = result.get(0);
        assertThat(dto.getId()).isEqualTo("DEF-005");
        assertThat(dto.getName()).isEqualTo("测试流程");
        assertThat(dto.getBizType()).isEqualTo("TARGET_ADJUST");
        assertThat(dto.getStatus()).isEqualTo("DRAFT");
        assertThat(dto.getVersion()).isEqualTo(2);
        assertThat(dto.getDeployedProcDefKey()).isEqualTo("key-123");
        assertThat(dto.getIsReadonlyImport()).isEqualTo(0);
    }

    // ------------------------------------------------------------------ //
    //  6. createImported_buildsReadonlyPublishedDef                       //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("createImported：新建只读已发布流程定义并落库图形数据")
    void createImported_buildsReadonlyPublishedDef() {
        FlowGraphDTO graph = buildSimpleGraph();

        String flowDefId = service.createImported(graph, "perf_alloc_adjust_corp_v1");

        assertThat(flowDefId).isNotBlank();

        // 捕获 insert 的 def，断言只读/已发布/flowKey/sourceProcDefKey
        ArgumentCaptor<WfFlowDef> defCaptor = ArgumentCaptor.forClass(WfFlowDef.class);
        verify(flowDefMapper).insert(defCaptor.capture());
        WfFlowDef def = defCaptor.getValue();
        assertThat(def.getIsReadonlyImport()).isEqualTo(1);
        assertThat(def.getStatus()).isEqualTo("PUBLISHED");
        assertThat(def.getVersion()).isEqualTo(0);
        assertThat(def.getFlowKey()).isEqualTo("imported_perf_alloc_adjust_corp_v1");
        assertThat(def.getSourceProcDefKey()).isEqualTo("perf_alloc_adjust_corp_v1");
        assertThat(def.getBizType()).isEqualTo("TARGET_ADJUST");

        // 落库图形数据：节点/边/审批人均被插入
        verify(nodeMapper, atLeast(2)).insert(ArgumentMatchers.<WfFlowNode>any(WfFlowNode.class));
        verify(edgeMapper, atLeast(1)).insert(ArgumentMatchers.<WfFlowEdge>any(WfFlowEdge.class));
        verify(approverMapper, atLeast(1)).insert(ArgumentMatchers.<WfFlowNodeApprover>any(WfFlowNodeApprover.class));
    }

    // ------------------------------------------------------------------ //
    //  辅助方法                                                            //
    // ------------------------------------------------------------------ //

    /** 构造一个状态为 DRAFT、非只读导入的流程定义 */
    private WfFlowDef draftDef(String id) {
        WfFlowDef def = new WfFlowDef();
        def.setId(id);
        def.setFlowKey("flow-key-" + id);
        def.setBizType("TARGET_ADJUST");
        def.setName("测试流程-" + id);
        def.setStatus("DRAFT");
        def.setVersion(0);
        def.setIsReadonlyImport(0);
        def.setCreatedBy("admin");
        def.setCreatedTime(LocalDateTime.now());
        def.setUpdatedTime(LocalDateTime.now());
        return def;
    }

    /** 构造一个节点 */
    private WfFlowNode node(String id, String flowDefId, String nodeKey) {
        WfFlowNode n = new WfFlowNode();
        n.setId(id);
        n.setFlowDefId(flowDefId);
        n.setNodeKey(nodeKey);
        n.setNodeType("APPROVE");
        n.setName("节点-" + nodeKey);
        n.setSortNo(1);
        return n;
    }

    /**
     * 构造最简图：
     * - nodeKey=start（START）
     * - nodeKey=a1（APPROVE，含1个approver）
     * - edge: start → a1
     */
    private FlowGraphDTO buildSimpleGraph() {
        FlowGraphDTO graph = new FlowGraphDTO();
        graph.setName("测试流程");
        graph.setBizType("TARGET_ADJUST");

        FlowNodeDTO startNode = new FlowNodeDTO();
        startNode.setNodeKey("start");
        startNode.setNodeType("START");
        startNode.setName("开始");
        startNode.setSortNo(1);

        FlowApproverDTO approver = new FlowApproverDTO();
        approver.setApproverType("ROLE");
        approver.setApproverValue("ROLE_MGR");

        FlowNodeDTO approveNode = new FlowNodeDTO();
        approveNode.setNodeKey("a1");
        approveNode.setNodeType("APPROVE");
        approveNode.setName("审批节点");
        approveNode.setSortNo(2);
        approveNode.setApprovers(List.of(approver));

        graph.setNodes(List.of(startNode, approveNode));

        FlowEdgeDTO edge = new FlowEdgeDTO();
        edge.setFromNodeKey("start");
        edge.setToNodeKey("a1");
        edge.setIsDefault(false);
        graph.setEdges(List.of(edge));

        return graph;
    }
}
