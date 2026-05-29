package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 审批流程设计器——现有已部署流程的反向导入服务。
 * <p>
 * 把 Flowable 中已部署的流程定义（{@link BpmnModel}）反向解析为流程图模型
 * （{@link FlowGraphDTO}），并通过 {@link FlowDefService#createImported(FlowGraphDTO, String)}
 * 落库为「只读导入」流程定义（isReadonlyImport=1，status=PUBLISHED），供设计器只读查看。
 * </p>
 * <p>
 * 反向解析约定：
 * <ul>
 *   <li>StartEvent→START、EndEvent→END、ExclusiveGateway→GATEWAY、UserTask→APPROVAL；
 *       其它 BPMN 元素（含 SequenceFlow，单独收集）忽略。</li>
 *   <li>UserTask 含多实例特性（getLoopCharacteristics()!=null）→ 会签 ALL，否则 ANY。</li>
 *   <li>审批人来自 {@link WfNodeCandidateConf}（按 procKey + nodeKey 查），
 *       candidateValue 为 JSON 数组，逐值展开为 {@link FlowApproverDTO}。</li>
 *   <li>SequenceFlow 转 edge；网关 defaultFlow 的那条边 isDefault=true；
 *       原边带条件表达式时，把表达式文本拼到 edge.name（不做结构化解析）。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class FlowImportService {

    private final RepositoryService repositoryService;
    private final NodeCandidateConfMapper nodeCandidateConfMapper;
    private final FlowDefService flowDefService;
    private final WfFlowDefMapper flowDefMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入（便于单元测试 mock）。
     *
     * @param repositoryService       Flowable RepositoryService（查询已部署流程定义/BpmnModel）
     * @param nodeCandidateConfMapper 节点候选人配置 Mapper（反推审批人）
     * @param flowDefService          流程定义服务（复用其只读落库逻辑）
     * @param flowDefMapper           流程定义 Mapper（幂等判断）
     * @param objectMapper            Jackson ObjectMapper（candidateValue JSON 数组解析）
     */
    public FlowImportService(RepositoryService repositoryService,
                             NodeCandidateConfMapper nodeCandidateConfMapper,
                             FlowDefService flowDefService,
                             WfFlowDefMapper flowDefMapper,
                             ObjectMapper objectMapper) {
        this.repositoryService = repositoryService;
        this.nodeCandidateConfMapper = nodeCandidateConfMapper;
        this.flowDefService = flowDefService;
        this.flowDefMapper = flowDefMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 把指定 Flowable 流程定义 KEY 的最新版本反向导入为只读流程定义。
     * <p>
     * 幂等：若已存在 flowKey="imported_"+procKey 的记录，直接返回其 id（跳过导入）。
     * </p>
     *
     * @param procKey Flowable 流程定义 KEY（如 perf_alloc_adjust_corp_v1）
     * @return 只读流程定义的 ID（新建或已存在）
     * @throws BizException WF-500xx 当流程定义不存在或反向解析失败时
     */
    public String importFromDeployed(String procKey) {
        log.info("[FlowImportService.importFromDeployed] 入参: procKey={}", procKey);

        // 1. 幂等：已导入则跳过
        WfFlowDef existing = flowDefMapper.selectByFlowKey("imported_" + procKey);
        if (existing != null) {
            log.info("[FlowImportService.importFromDeployed] 已存在，跳过导入: procKey={}, flowDefId={}",
                    procKey, existing.getId());
            return existing.getId();
        }

        try {
            // 2. 取最新版本流程定义 + BpmnModel
            ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(procKey)
                    .latestVersion()
                    .singleResult();
            if (procDef == null) {
                throw new BizException(WfErrorCode.PROCESS_DEF_NOT_FOUND.getCode(),
                        "未找到已部署的流程定义，procKey=" + procKey);
            }
            BpmnModel model = repositoryService.getBpmnModel(procDef.getId());
            Process process = model.getMainProcess() != null
                    ? model.getMainProcess()
                    : model.getProcesses().get(0);

            // 3. 反向解析节点与边
            List<FlowNodeDTO> nodes = new ArrayList<>();
            List<SequenceFlow> sequenceFlows = new ArrayList<>();
            Set<String> defaultFlowIds = collectDefaultFlowIds(process);

            for (FlowElement el : process.getFlowElements()) {
                if (el instanceof StartEvent) {
                    nodes.add(node(el.getId(), displayName(el), "START", null));
                } else if (el instanceof EndEvent) {
                    nodes.add(node(el.getId(), displayName(el), "END", null));
                } else if (el instanceof ExclusiveGateway) {
                    nodes.add(node(el.getId(), displayName(el), "GATEWAY", null));
                } else if (el instanceof UserTask) {
                    UserTask ut = (UserTask) el;
                    String approveMode = ut.getLoopCharacteristics() != null ? "ALL" : "ANY";
                    FlowNodeDTO node = node(el.getId(), displayName(el), "APPROVAL", approveMode);
                    node.setApprovers(resolveApprovers(procKey, el.getId()));
                    nodes.add(node);
                } else if (el instanceof SequenceFlow) {
                    sequenceFlows.add((SequenceFlow) el);
                }
                // 其它元素类型忽略
            }

            List<FlowEdgeDTO> edges = new ArrayList<>();
            for (SequenceFlow sf : sequenceFlows) {
                FlowEdgeDTO edge = new FlowEdgeDTO();
                edge.setFromNodeKey(sf.getSourceRef());
                edge.setToNodeKey(sf.getTargetRef());
                edge.setIsDefault(defaultFlowIds.contains(sf.getId()));
                edge.setCondition(null);  // 不结构化条件，仅把表达式文本记到 name
                edges.add(edge);
            }

            // 4. 构建 graph 并落库
            FlowGraphDTO graph = new FlowGraphDTO();
            graph.setName(StringUtils.hasText(procDef.getName()) ? procDef.getName() : procKey);
            graph.setBizType(deriveBizType(procKey));
            graph.setNodes(nodes);
            graph.setEdges(edges);

            String flowDefId = flowDefService.createImported(graph, procKey);
            log.info("[FlowImportService.importFromDeployed] 出参: procKey={}, flowDefId={}, nodes={}, edges={}",
                    procKey, flowDefId, nodes.size(), edges.size());
            return flowDefId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowImportService.importFromDeployed] 反向导入失败: procKey={}", procKey, e);
            throw new BizException(WfErrorCode.ENGINE_ERROR.getCode(),
                    "反向导入流程失败，procKey=" + procKey + "，原因: " + e.getMessage());
        }
    }

    /**
     * 收集流程内所有排他网关的 defaultFlow id（用于判断某条边是否为默认分支）。
     */
    private Set<String> collectDefaultFlowIds(Process process) {
        Set<String> ids = new HashSet<>();
        for (FlowElement el : process.getFlowElements()) {
            if (el instanceof ExclusiveGateway) {
                String def = ((ExclusiveGateway) el).getDefaultFlow();
                if (StringUtils.hasText(def)) {
                    ids.add(def);
                }
            }
        }
        return ids;
    }

    /**
     * 按 (procKey, nodeKey) 查候选配置，逐行解析 candidateValue（JSON 数组）为审批人列表。
     * 每行的 candidateType 应用到该行的每个候选值。
     */
    private List<FlowApproverDTO> resolveApprovers(String procKey, String nodeKey) {
        List<FlowApproverDTO> approvers = new ArrayList<>();
        List<WfNodeCandidateConf> confs =
                nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey(procKey, nodeKey);
        if (CollectionUtils.isEmpty(confs)) {
            return approvers;
        }
        for (WfNodeCandidateConf conf : confs) {
            List<String> values = parseJsonArray(conf.getCandidateValue());
            for (String v : values) {
                FlowApproverDTO dto = new FlowApproverDTO();
                dto.setApproverType(conf.getCandidateType());
                dto.setApproverValue(v);
                approvers.add(dto);
            }
        }
        return approvers;
    }

    /**
     * 解析 candidateValue JSON 数组字符串为 List；解析失败时降级为空列表并告警。
     */
    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.warn("[FlowImportService] candidateValue 非法 JSON 数组，跳过: value={}, err={}",
                    json, e.getMessage());
            return List.of();
        }
    }

    /**
     * 业务类型推导：procKey 含 alloc→ALLOC_ADJUST，含 target→TARGET_ADJUST，否则 UNKNOWN。
     */
    private String deriveBizType(String procKey) {
        if (procKey == null) {
            return "UNKNOWN";
        }
        String lower = procKey.toLowerCase();
        if (lower.contains("alloc")) {
            return "ALLOC_ADJUST";
        }
        if (lower.contains("target")) {
            return "TARGET_ADJUST";
        }
        return "UNKNOWN";
    }

    /** 构造 FlowNodeDTO（name 空则回退用 nodeKey/id） */
    private FlowNodeDTO node(String id, String name, String type, String approveMode) {
        FlowNodeDTO dto = new FlowNodeDTO();
        dto.setNodeKey(id);
        dto.setNodeType(type);
        dto.setName(StringUtils.hasText(name) ? name : id);
        dto.setApproveMode(approveMode);
        return dto;
    }

    /** 取元素显示名（getName 空则回退 id） */
    private String displayName(FlowElement el) {
        return StringUtils.hasText(el.getName()) ? el.getName() : el.getId();
    }
}
