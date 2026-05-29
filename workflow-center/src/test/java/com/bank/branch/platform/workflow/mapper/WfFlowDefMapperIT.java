package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfFlowEdge;
import com.bank.branch.platform.workflow.entity.WfFlowNode;
import com.bank.branch.platform.workflow.entity.WfFlowNodeApprover;
import com.bank.branch.platform.workflow.support.WfMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WfFlowDef / WfFlowNode / WfFlowNodeApprover / WfFlowEdge Mapper 集成测试。
 * <p>
 * 测试数据前缀 FD_T1 / FN_T1_* / FA_T1_* / FE_T1_* 避免与其他测试数据冲突。
 * 方法级 @Transactional + @Rollback(true)（继承自 WfMapperTestBase），测试完自动回滚。
 * </p>
 */
class WfFlowDefMapperIT extends WfMapperTestBase {

    @Autowired
    private WfFlowDefMapper flowDefMapper;

    @Autowired
    private WfFlowNodeMapper flowNodeMapper;

    @Autowired
    private WfFlowNodeApproverMapper approverMapper;

    @Autowired
    private WfFlowEdgeMapper edgeMapper;

    // ===== 测试数据常量（独立前缀，避免与他人冲突）=====
    private static final String FLOW_DEF_ID   = "FD_T1";
    private static final String NODE_ID_1     = "FN_T1_01";
    private static final String NODE_ID_2     = "FN_T1_02";
    private static final String APPROVER_ID_1 = "FA_T1_01";
    private static final String APPROVER_ID_2 = "FA_T1_02";
    private static final String EDGE_ID_1     = "FE_T1_01";

    // ===== 辅助构建方法 =====

    private WfFlowDef buildFlowDef() {
        WfFlowDef def = new WfFlowDef();
        def.setId(FLOW_DEF_ID);
        def.setFlowKey("t1");
        def.setBizType("ALLOC_ADJUST");
        def.setName("测试流程");
        def.setStatus("DRAFT");
        def.setVersion(0);
        def.setIsReadonlyImport(0);
        def.setCreatedTime(LocalDateTime.now());
        def.setUpdatedTime(LocalDateTime.now());
        return def;
    }

    private WfFlowNode buildNode(String id, String nodeKey, int sortNo) {
        WfFlowNode node = new WfFlowNode();
        node.setId(id);
        node.setFlowDefId(FLOW_DEF_ID);
        node.setNodeKey(nodeKey);
        node.setNodeType("APPROVE");
        node.setName("审批节点-" + nodeKey);
        node.setSortNo(sortNo);
        node.setPosX(100);
        node.setPosY(200);
        node.setCreatedTime(LocalDateTime.now());
        node.setUpdatedTime(LocalDateTime.now());
        return node;
    }

    private WfFlowNodeApprover buildApprover(String id, String nodeId, int sortNo) {
        WfFlowNodeApprover approver = new WfFlowNodeApprover();
        approver.setId(id);
        approver.setNodeId(nodeId);
        approver.setApproverType("ROLE");
        approver.setApproverValue("BRANCH_HEAD");
        approver.setSortNo(sortNo);
        approver.setCreatedTime(LocalDateTime.now());
        return approver;
    }

    private WfFlowEdge buildEdge(String id, String fromNodeId, String toNodeId) {
        WfFlowEdge edge = new WfFlowEdge();
        edge.setId(id);
        edge.setFlowDefId(FLOW_DEF_ID);
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        edge.setName("顺序流");
        edge.setIsDefault(1);
        edge.setSortNo(1);
        edge.setCreatedTime(LocalDateTime.now());
        return edge;
    }

    // ===== 测试用例 =====

    @Test
    @DisplayName("insert WfFlowDef 后可按 id 查回，按 flowKey 查回非空")
    void insertFlowDef_thenSelectByIdAndByFlowKey() {
        // 插入流程定义
        flowDefMapper.insert(buildFlowDef());

        // 按 id 查回
        WfFlowDef loaded = flowDefMapper.selectById(FLOW_DEF_ID);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getFlowKey()).isEqualTo("t1");
        assertThat(loaded.getBizType()).isEqualTo("ALLOC_ADJUST");
        assertThat(loaded.getName()).isEqualTo("测试流程");
        assertThat(loaded.getStatus()).isEqualTo("DRAFT");

        // 按 flowKey 查回
        WfFlowDef byKey = flowDefMapper.selectByFlowKey("t1");
        assertThat(byKey).isNotNull();
        assertThat(byKey.getId()).isEqualTo(FLOW_DEF_ID);
    }

    @Test
    @DisplayName("selectByFlowKey 不存在时返回 null")
    void selectByFlowKey_whenNotExists_returnsNull() {
        assertThat(flowDefMapper.selectByFlowKey("not_exist_key_t1")).isNull();
    }

    @Test
    @DisplayName("插入 2 个节点，selectByFlowDefId 返回 2 条，按 sort_no 升序")
    void insertNodes_thenSelectByFlowDefId_returns2() {
        flowDefMapper.insert(buildFlowDef());
        flowNodeMapper.insert(buildNode(NODE_ID_1, "start_node", 1));
        flowNodeMapper.insert(buildNode(NODE_ID_2, "approve_node", 2));

        List<WfFlowNode> nodes = flowNodeMapper.selectByFlowDefId(FLOW_DEF_ID);

        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).getSortNo()).isLessThanOrEqualTo(nodes.get(1).getSortNo());
        assertThat(nodes).extracting(WfFlowNode::getNodeKey)
                .containsExactlyInAnyOrder("start_node", "approve_node");
    }

    @Test
    @DisplayName("插入 2 个审批人，selectByNodeIds 返回 2 条")
    void insertApprovers_thenSelectByNodeIds_returns2() {
        flowDefMapper.insert(buildFlowDef());
        flowNodeMapper.insert(buildNode(NODE_ID_1, "n1", 1));
        flowNodeMapper.insert(buildNode(NODE_ID_2, "n2", 2));
        approverMapper.insert(buildApprover(APPROVER_ID_1, NODE_ID_1, 1));
        approverMapper.insert(buildApprover(APPROVER_ID_2, NODE_ID_2, 1));

        List<WfFlowNodeApprover> approvers =
                approverMapper.selectByNodeIds(List.of(NODE_ID_1, NODE_ID_2));

        assertThat(approvers).hasSize(2);
        assertThat(approvers).extracting(WfFlowNodeApprover::getNodeId)
                .containsExactlyInAnyOrder(NODE_ID_1, NODE_ID_2);
    }

    @Test
    @DisplayName("插入 1 条连线，selectByFlowDefId 返回 1 条")
    void insertEdge_thenSelectByFlowDefId_returns1() {
        flowDefMapper.insert(buildFlowDef());
        flowNodeMapper.insert(buildNode(NODE_ID_1, "n1", 1));
        flowNodeMapper.insert(buildNode(NODE_ID_2, "n2", 2));
        edgeMapper.insert(buildEdge(EDGE_ID_1, NODE_ID_1, NODE_ID_2));

        List<WfFlowEdge> edges = edgeMapper.selectByFlowDefId(FLOW_DEF_ID);

        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).getFromNodeId()).isEqualTo(NODE_ID_1);
        assertThat(edges.get(0).getToNodeId()).isEqualTo(NODE_ID_2);
    }

    @Test
    @DisplayName("deleteByFlowDefId 后 selectByFlowDefId 返回空列表")
    void deleteNodesByFlowDefId_thenSelectReturnsEmpty() {
        flowDefMapper.insert(buildFlowDef());
        flowNodeMapper.insert(buildNode(NODE_ID_1, "n1", 1));
        flowNodeMapper.insert(buildNode(NODE_ID_2, "n2", 2));

        // 确认删前有数据
        assertThat(flowNodeMapper.selectByFlowDefId(FLOW_DEF_ID)).hasSize(2);

        // 执行删除
        int deleted = flowNodeMapper.deleteByFlowDefId(FLOW_DEF_ID);
        assertThat(deleted).isEqualTo(2);

        // 删后为空
        assertThat(flowNodeMapper.selectByFlowDefId(FLOW_DEF_ID)).isEmpty();
    }

    @Test
    @DisplayName("deleteByFlowDefId 后 selectByFlowDefId(edge) 返回空列表")
    void deleteEdgesByFlowDefId_thenSelectReturnsEmpty() {
        flowDefMapper.insert(buildFlowDef());
        flowNodeMapper.insert(buildNode(NODE_ID_1, "n1", 1));
        flowNodeMapper.insert(buildNode(NODE_ID_2, "n2", 2));
        edgeMapper.insert(buildEdge(EDGE_ID_1, NODE_ID_1, NODE_ID_2));

        assertThat(edgeMapper.selectByFlowDefId(FLOW_DEF_ID)).hasSize(1);

        int deleted = edgeMapper.deleteByFlowDefId(FLOW_DEF_ID);
        assertThat(deleted).isEqualTo(1);

        assertThat(edgeMapper.selectByFlowDefId(FLOW_DEF_ID)).isEmpty();
    }

    @Test
    @DisplayName("deleteByNodeIds 后 selectByNodeIds 返回空列表")
    void deleteApproversByNodeIds_thenSelectReturnsEmpty() {
        flowDefMapper.insert(buildFlowDef());
        flowNodeMapper.insert(buildNode(NODE_ID_1, "n1", 1));
        flowNodeMapper.insert(buildNode(NODE_ID_2, "n2", 2));
        approverMapper.insert(buildApprover(APPROVER_ID_1, NODE_ID_1, 1));
        approverMapper.insert(buildApprover(APPROVER_ID_2, NODE_ID_2, 1));

        assertThat(approverMapper.selectByNodeIds(List.of(NODE_ID_1, NODE_ID_2))).hasSize(2);

        int deleted = approverMapper.deleteByNodeIds(List.of(NODE_ID_1, NODE_ID_2));
        assertThat(deleted).isEqualTo(2);

        assertThat(approverMapper.selectByNodeIds(List.of(NODE_ID_1, NODE_ID_2))).isEmpty();
    }
}
