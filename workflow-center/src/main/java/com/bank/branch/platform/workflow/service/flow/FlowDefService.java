package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowDefDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfFlowEdge;
import com.bank.branch.platform.workflow.entity.WfFlowNode;
import com.bank.branch.platform.workflow.entity.WfFlowNodeApprover;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowEdgeMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowNodeApproverMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowNodeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审批流程设计器——流程定义草稿 CRUD 服务。
 * <p>
 * 负责流程定义（{@link WfFlowDef}）及其图形数据（节点、连线、审批人）的创建、
 * 查询、整图替换保存和草稿删除等操作。所有写操作均在事务内执行。
 * </p>
 *
 * <pre>
 * 主要方法：
 *   listAll()                  - 查询所有流程定义，转为 DTO 列表
 *   getGraph(flowDefId)        - 获取指定流程定义的完整图形数据
 *   create(graph, operator)    - 新建流程定义及其图形数据，返回新 flowDefId
 *   saveGraph(...)             - 整图替换保存（先删后插），仅 DRAFT 且非只读可操作
 *   deleteDraft(flowDefId)     - 删除草稿（仅 DRAFT 且非只读可删除）
 * </pre>
 */
@Slf4j
@Service
public class FlowDefService {

    private final WfFlowDefMapper flowDefMapper;
    private final WfFlowNodeMapper nodeMapper;
    private final WfFlowNodeApproverMapper approverMapper;
    private final WfFlowEdgeMapper edgeMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入（便于单元测试 mock）。
     *
     * @param flowDefMapper  流程定义 Mapper
     * @param nodeMapper     节点 Mapper
     * @param approverMapper 审批人规则 Mapper
     * @param edgeMapper     连线 Mapper
     * @param objectMapper   Jackson ObjectMapper（condition JSON 序列化/反序列化）
     */
    public FlowDefService(WfFlowDefMapper flowDefMapper,
                          WfFlowNodeMapper nodeMapper,
                          WfFlowNodeApproverMapper approverMapper,
                          WfFlowEdgeMapper edgeMapper,
                          ObjectMapper objectMapper) {
        this.flowDefMapper = flowDefMapper;
        this.nodeMapper = nodeMapper;
        this.approverMapper = approverMapper;
        this.edgeMapper = edgeMapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ //
    //  查询                                                               //
    // ------------------------------------------------------------------ //

    /**
     * 查询所有流程定义，转换为 DTO 列表（不含图形数据）。
     *
     * @return 流程定义 DTO 列表，按数据库默认顺序返回
     */
    public List<FlowDefDTO> listAll() {
        List<WfFlowDef> defs = flowDefMapper.selectList(null);
        return defs.stream().map(this::toDefDTO).collect(Collectors.toList());
    }

    /**
     * 获取指定流程定义的完整图形数据（节点 + 连线 + 审批人）。
     * <p>
     * edge 的 fromNodeId/toNodeId 会通过 nodeId→nodeKey 映射回 nodeKey，
     * approver 规则会挂到对应节点的 approvers 列表中。
     * </p>
     *
     * @param flowDefId 流程定义ID
     * @return 流程图 DTO，入参/出参均记录日志
     * @throws BizException WF-40400 当流程定义不存在时
     */
    public FlowGraphDTO getGraph(String flowDefId) {
        log.info("[FlowDefService.getGraph] 入参: flowDefId={}", flowDefId);

        WfFlowDef def = requireDef(flowDefId);
        List<WfFlowNode> nodes = nodeMapper.selectByFlowDefId(flowDefId);
        List<WfFlowEdge> edges = edgeMapper.selectByFlowDefId(flowDefId);

        // 构建 nodeId → nodeKey 和 nodeId → FlowNodeDTO 映射
        Map<String, String> idToKey = new HashMap<>();
        Map<String, FlowNodeDTO> idToNodeDTO = new HashMap<>();
        List<FlowNodeDTO> nodeDTOs = new ArrayList<>();

        for (WfFlowNode node : nodes) {
            FlowNodeDTO dto = toNodeDTO(node);
            dto.setApprovers(new ArrayList<>());  // 先初始化空列表，后续填充
            idToKey.put(node.getId(), node.getNodeKey());
            idToNodeDTO.put(node.getId(), dto);
            nodeDTOs.add(dto);
        }

        // 加载审批人并挂到对应节点
        if (!nodes.isEmpty()) {
            List<String> nodeIds = nodes.stream().map(WfFlowNode::getId).collect(Collectors.toList());
            List<WfFlowNodeApprover> approvers = approverMapper.selectByNodeIds(nodeIds);
            for (WfFlowNodeApprover approver : approvers) {
                FlowNodeDTO nodeDTO = idToNodeDTO.get(approver.getNodeId());
                if (nodeDTO != null) {
                    nodeDTO.getApprovers().add(toApproverDTO(approver));
                }
            }
        }

        // 转换 edges，nodeId → nodeKey
        List<FlowEdgeDTO> edgeDTOs = edges.stream()
                .map(e -> toEdgeDTO(e, idToKey))
                .collect(Collectors.toList());

        FlowGraphDTO result = new FlowGraphDTO();
        result.setName(def.getName());
        result.setBizType(def.getBizType());
        result.setNodes(nodeDTOs);
        result.setEdges(edgeDTOs);

        log.info("[FlowDefService.getGraph] 出参: flowDefId={}, nodes={}, edges={}",
                flowDefId, nodeDTOs.size(), edgeDTOs.size());
        return result;
    }

    // ------------------------------------------------------------------ //
    //  写操作                                                             //
    // ------------------------------------------------------------------ //

    /**
     * 新建流程定义草稿，并保存初始图形数据。
     * <p>
     * 新建时 status=DRAFT，version=0，isReadonlyImport=0。
     * flowKey 取 graph.bizType + UUID 前 8 位组合生成。
     * </p>
     *
     * @param graph    流程图 DTO（含节点、连线、审批人配置）
     * @param operator 操作人工号（写入 created_by / updated_by）
     * @return 新建流程定义的 ID（UUID，无横线）
     */
    @Transactional
    public String create(FlowGraphDTO graph, String operator) {
        log.info("[FlowDefService.create] 入参: bizType={}, operator={}", graph.getBizType(), operator);

        String flowDefId = newUuid();
        String flowKey = (graph.getBizType() != null ? graph.getBizType() : "FLOW")
                + "_" + flowDefId.substring(0, 8);

        WfFlowDef def = new WfFlowDef();
        def.setId(flowDefId);
        def.setFlowKey(flowKey);
        def.setBizType(graph.getBizType());
        def.setName(graph.getName());
        def.setStatus("DRAFT");
        def.setVersion(0);
        def.setIsReadonlyImport(0);
        def.setCreatedBy(operator);
        def.setCreatedTime(LocalDateTime.now());
        def.setUpdatedBy(operator);
        def.setUpdatedTime(LocalDateTime.now());

        flowDefMapper.insert(def);

        // 保存图形数据（新建时无旧数据，内部 insertGraphElements 仅插入不删除）
        insertGraphElements(flowDefId, graph);

        log.info("[FlowDefService.create] 出参: flowDefId={}", flowDefId);
        return flowDefId;
    }

    /**
     * 反向导入：把已部署的现有流程图保存为只读流程定义。
     * <p>
     * 与 {@link #create(FlowGraphDTO, String)} 复用同一套落库逻辑
     * （{@link #insertGraphElements(String, FlowGraphDTO)}），区别在于 def 元数据：
     * status=PUBLISHED、version=0、isReadonlyImport=1、flowKey="imported_"+sourceProcKey、
     * sourceProcDefKey=sourceProcKey，操作人固定为 system。
     * </p>
     * <p>
     * 注意：本方法只负责落库，不做幂等判断（由调用方 FlowImportService 负责），
     * 也不校验图结构合法性（导入数据来自已部署流程，视为可信）。
     * </p>
     *
     * @param graph         反向解析得到的流程图 DTO
     * @param sourceProcKey 来源 Flowable 流程定义 KEY
     * @return 新建流程定义的 ID（UUID，无横线）
     */
    @Transactional
    public String createImported(FlowGraphDTO graph, String sourceProcKey) {
        log.info("[FlowDefService.createImported] 入参: sourceProcKey={}, bizType={}",
                sourceProcKey, graph.getBizType());

        String flowDefId = newUuid();

        WfFlowDef def = new WfFlowDef();
        def.setId(flowDefId);
        def.setFlowKey("imported_" + sourceProcKey);
        def.setBizType(graph.getBizType());
        def.setName(graph.getName());
        def.setStatus("PUBLISHED");
        def.setVersion(0);
        def.setIsReadonlyImport(1);
        def.setSourceProcDefKey(sourceProcKey);
        def.setCreatedBy("system");
        def.setCreatedTime(LocalDateTime.now());
        def.setUpdatedBy("system");
        def.setUpdatedTime(LocalDateTime.now());

        flowDefMapper.insert(def);

        // 复用与 create 一致的图形落库逻辑
        insertGraphElements(flowDefId, graph);

        log.info("[FlowDefService.createImported] 出参: flowDefId={}", flowDefId);
        return flowDefId;
    }

    /**
     * 整图替换保存流程图（先删除旧节点/连线/审批人，再插入新数据）。
     * <p>
     * 约束：
     * <ul>
     *   <li>流程定义必须存在</li>
     *   <li>isReadonlyImport=1 时禁止修改，抛 BizException（WF-40400）</li>
     * </ul>
     * </p>
     *
     * @param flowDefId 流程定义ID
     * @param graph     新的流程图 DTO
     * @param operator  操作人工号
     * @throws BizException WF-40400 当流程不存在或为只读导入时
     */
    @Transactional
    public void saveGraph(String flowDefId, FlowGraphDTO graph, String operator) {
        log.info("[FlowDefService.saveGraph] 入参: flowDefId={}, operator={}", flowDefId, operator);

        WfFlowDef def = requireDef(flowDefId);

        // 只读导入的流程定义禁止修改
        if (Integer.valueOf(1).equals(def.getIsReadonlyImport())) {
            log.warn("[FlowDefService.saveGraph] 只读导入流程禁止修改: flowDefId={}", flowDefId);
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "只读导入的流程定义不允许修改，flowDefId=" + flowDefId);
        }

        // 删除旧数据
        List<WfFlowNode> oldNodes = nodeMapper.selectByFlowDefId(flowDefId);
        if (!CollectionUtils.isEmpty(oldNodes)) {
            List<String> oldNodeIds = oldNodes.stream()
                    .map(WfFlowNode::getId).collect(Collectors.toList());
            approverMapper.deleteByNodeIds(oldNodeIds);
        }
        edgeMapper.deleteByFlowDefId(flowDefId);
        nodeMapper.deleteByFlowDefId(flowDefId);

        // 插入新图形数据
        insertGraphElements(flowDefId, graph);

        // 更新 def 的 name / updatedBy / updatedTime
        def.setName(graph.getName());
        def.setUpdatedBy(operator);
        def.setUpdatedTime(LocalDateTime.now());
        flowDefMapper.updateById(def);

        log.info("[FlowDefService.saveGraph] 完成: flowDefId={}", flowDefId);
    }

    /**
     * 删除草稿流程定义（级联删除节点、连线、审批人）。
     * <p>
     * 约束：仅 status=DRAFT 且 isReadonlyImport=0 的流程允许删除。
     * </p>
     *
     * @param flowDefId 流程定义ID
     * @throws BizException WF-40400 当流程不存在、已发布或为只读导入时
     */
    @Transactional
    public void deleteDraft(String flowDefId) {
        log.info("[FlowDefService.deleteDraft] 入参: flowDefId={}", flowDefId);

        WfFlowDef def = requireDef(flowDefId);

        // 只读导入的流程不允许删除
        if (Integer.valueOf(1).equals(def.getIsReadonlyImport())) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "只读导入的流程定义不允许删除，flowDefId=" + flowDefId);
        }

        // 非 DRAFT 状态不允许删除
        if (!"DRAFT".equals(def.getStatus())) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "仅允许删除草稿状态的流程定义，当前状态=" + def.getStatus() + "，flowDefId=" + flowDefId);
        }

        // 级联删除：approvers → edges → nodes → def
        List<WfFlowNode> nodes = nodeMapper.selectByFlowDefId(flowDefId);
        if (!CollectionUtils.isEmpty(nodes)) {
            List<String> nodeIds = nodes.stream()
                    .map(WfFlowNode::getId).collect(Collectors.toList());
            approverMapper.deleteByNodeIds(nodeIds);
        }
        edgeMapper.deleteByFlowDefId(flowDefId);
        nodeMapper.deleteByFlowDefId(flowDefId);
        flowDefMapper.deleteById(flowDefId);

        log.info("[FlowDefService.deleteDraft] 完成: flowDefId={}", flowDefId);
    }

    // ------------------------------------------------------------------ //
    //  内部方法                                                           //
    // ------------------------------------------------------------------ //

    /**
     * 内部方法：插入图形数据（节点、审批人、连线），供 create / saveGraph / createImported 共用。
     * 先插入所有节点并建立 nodeKey→nodeId 映射，再插入 approvers 和 edges。
     *
     * @param flowDefId 流程定义ID
     * @param graph     流程图 DTO
     */
    private void insertGraphElements(String flowDefId, FlowGraphDTO graph) {
        List<FlowNodeDTO> nodeDTOs = graph.getNodes() != null ? graph.getNodes() : Collections.emptyList();
        List<FlowEdgeDTO> edgeDTOs = graph.getEdges() != null ? graph.getEdges() : Collections.emptyList();

        // nodeKey → 新生成的 nodeId
        Map<String, String> keyToId = new HashMap<>();

        // 1. 插入节点
        for (int i = 0; i < nodeDTOs.size(); i++) {
            FlowNodeDTO dto = nodeDTOs.get(i);
            String nodeId = newUuid();
            keyToId.put(dto.getNodeKey(), nodeId);

            WfFlowNode node = new WfFlowNode();
            node.setId(nodeId);
            node.setFlowDefId(flowDefId);
            node.setNodeKey(dto.getNodeKey());
            node.setNodeType(dto.getNodeType());
            node.setName(dto.getName());
            node.setApproveMode(dto.getApproveMode());
            // 审批机构归属（本机构/上级机构/不判断）随整图落库
            node.setApproveOrgScope(dto.getApproveOrgScope());
            node.setSortNo(dto.getSortNo() != null ? dto.getSortNo() : i + 1);
            // 画布坐标（可视化流程图编辑器节点位置）随整图落库
            node.setPosX(dto.getPosX());
            node.setPosY(dto.getPosY());
            node.setCreatedTime(LocalDateTime.now());
            node.setUpdatedTime(LocalDateTime.now());
            nodeMapper.insert(node);

            // 2. 插入该节点的审批人规则
            List<FlowApproverDTO> approvers = dto.getApprovers();
            if (!CollectionUtils.isEmpty(approvers)) {
                for (int j = 0; j < approvers.size(); j++) {
                    FlowApproverDTO adto = approvers.get(j);
                    WfFlowNodeApprover approver = new WfFlowNodeApprover();
                    approver.setId(newUuid());
                    approver.setNodeId(nodeId);
                    approver.setApproverType(adto.getApproverType());
                    approver.setApproverValue(adto.getApproverValue());
                    // 层级角色的层级 / 机构角色的可选角色随审批人落库
                    approver.setOrgScope(adto.getOrgScope());
                    approver.setRoleCode(adto.getRoleCode());
                    approver.setSortNo(j + 1);
                    approver.setCreatedTime(LocalDateTime.now());
                    approverMapper.insert(approver);
                }
            }
        }

        // 3. 插入连线（nodeKey → nodeId 映射）
        for (int i = 0; i < edgeDTOs.size(); i++) {
            FlowEdgeDTO dto = edgeDTOs.get(i);
            WfFlowEdge edge = new WfFlowEdge();
            edge.setId(newUuid());
            edge.setFlowDefId(flowDefId);
            edge.setFromNodeId(keyToId.get(dto.getFromNodeKey()));
            edge.setToNodeId(keyToId.get(dto.getToNodeKey()));
            // 分支「输出名称」承载在 name 列
            edge.setName(dto.getOutputName());
            edge.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()) ? 1 : 0);
            edge.setSortNo(i + 1);
            edge.setCreatedTime(LocalDateTime.now());

            // condition 序列化为 JSON
            if (dto.getCondition() != null) {
                try {
                    edge.setConditionJson(objectMapper.writeValueAsString(dto.getCondition()));
                } catch (JsonProcessingException e) {
                    log.warn("[FlowDefService] condition JSON 序列化失败，跳过: {}", e.getMessage());
                }
            }
            edgeMapper.insert(edge);
        }
    }

    /**
     * 按 ID 加载流程定义，不存在时抛 BizException（WF-40400）。
     *
     * @param flowDefId 流程定义ID
     * @return 流程定义实体
     */
    private WfFlowDef requireDef(String flowDefId) {
        WfFlowDef def = flowDefMapper.selectById(flowDefId);
        if (def == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "流程定义不存在，flowDefId=" + flowDefId);
        }
        return def;
    }

    /** 生成去横线 UUID */
    private String newUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ------------------------------------------------------------------ //
    //  DTO 转换                                                           //
    // ------------------------------------------------------------------ //

    private FlowDefDTO toDefDTO(WfFlowDef def) {
        FlowDefDTO dto = new FlowDefDTO();
        dto.setId(def.getId());
        dto.setFlowKey(def.getFlowKey());
        dto.setBizType(def.getBizType());
        dto.setName(def.getName());
        dto.setStatus(def.getStatus());
        dto.setVersion(def.getVersion());
        dto.setDeployedProcDefKey(def.getDeployedProcDefKey());
        dto.setIsReadonlyImport(def.getIsReadonlyImport());
        dto.setUpdatedTime(def.getUpdatedTime());
        return dto;
    }

    private FlowNodeDTO toNodeDTO(WfFlowNode node) {
        FlowNodeDTO dto = new FlowNodeDTO();
        dto.setNodeKey(node.getNodeKey());
        dto.setNodeType(node.getNodeType());
        dto.setName(node.getName());
        dto.setApproveMode(node.getApproveMode());
        // 审批机构归属回传前端，供节点属性面板还原
        dto.setApproveOrgScope(node.getApproveOrgScope());
        dto.setSortNo(node.getSortNo());
        // 画布坐标回传前端，供可视化编辑器还原节点位置
        dto.setPosX(node.getPosX());
        dto.setPosY(node.getPosY());
        return dto;
    }

    private FlowApproverDTO toApproverDTO(WfFlowNodeApprover approver) {
        FlowApproverDTO dto = new FlowApproverDTO();
        dto.setApproverType(approver.getApproverType());
        dto.setApproverValue(approver.getApproverValue());
        dto.setOrgScope(approver.getOrgScope());
        dto.setRoleCode(approver.getRoleCode());
        return dto;
    }

    private FlowEdgeDTO toEdgeDTO(WfFlowEdge edge, Map<String, String> nodeIdToKey) {
        FlowEdgeDTO dto = new FlowEdgeDTO();
        dto.setFromNodeKey(nodeIdToKey.get(edge.getFromNodeId()));
        dto.setToNodeKey(nodeIdToKey.get(edge.getToNodeId()));
        // name 列回传为分支「输出名称」
        dto.setOutputName(edge.getName());
        dto.setIsDefault(Integer.valueOf(1).equals(edge.getIsDefault()));

        // conditionJson 反序列化为 FlowConditionDTO
        if (edge.getConditionJson() != null) {
            try {
                dto.setCondition(objectMapper.readValue(edge.getConditionJson(), FlowConditionDTO.class));
            } catch (JsonProcessingException e) {
                log.warn("[FlowDefService] condition JSON 反序列化失败，忽略: edgeId={}, err={}",
                        edge.getId(), e.getMessage());
            }
        }
        return dto;
    }
}
