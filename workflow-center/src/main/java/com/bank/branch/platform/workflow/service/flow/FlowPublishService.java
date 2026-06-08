package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.flow.FlowApproverDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowNodeDTO;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批流程设计器——流程发布服务。
 * <p>
 * 把可视化流程定义草稿编译并部署为可运行的 Flowable 流程：
 * </p>
 * <ol>
 *   <li>加载流程图（{@link FlowDefService#getGraph}）与流程定义实体；</li>
 *   <li>用 {@link FlowValidator} 做发布前结构校验，校验不通过则抛 {@link BizException}
 *       且<b>不</b>进行任何部署；</li>
 *   <li>用 {@link FlowBpmnGenerator} 生成 {@link BpmnModel}，序列化为 BPMN 2.0 XML；</li>
 *   <li>把 XML 以影子 KEY（{@code DSN_ + flowKey}）部署到 Flowable RepositoryService；</li>
 *   <li>按最新审批人规则整图替换 {@link WfNodeCandidateConf}（先删后插）；</li>
 *   <li>回填流程定义：status=PUBLISHED、version+1、回填部署的 procDefKey / procDefId。</li>
 * </ol>
 *
 * <p>设计要点：候选人配置以影子 KEY 为粒度先删后插，保证发布后运行时
 * {@code TaskAssignmentListener} / {@code MultiInstanceApproverResolver} 读到的候选规则
 * 与本次发布的图严格一致；只有校验通过才会触碰 Flowable 与数据库，避免脏部署。</p>
 */
@Slf4j
@Service
public class FlowPublishService {

    /** 影子流程 KEY 前缀：设计器生成的流程统一加该前缀，与导入/历史静态流程区分 */
    private static final String SHADOW_KEY_PREFIX = "DSN_";

    private final FlowDefService flowDefService;
    private final WfFlowDefMapper flowDefMapper;
    private final FlowValidator flowValidator;
    private final FlowBpmnGenerator bpmnGenerator;
    private final RepositoryService repositoryService;
    private final NodeCandidateConfMapper nodeCandidateConfMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入（便于单元测试 mock 全部协作者）。
     *
     * @param flowDefService          流程图查询服务
     * @param flowDefMapper           流程定义 Mapper
     * @param flowValidator           发布前校验器
     * @param bpmnGenerator           BPMN 生成器
     * @param repositoryService       Flowable 部署服务
     * @param nodeCandidateConfMapper 候选人配置 Mapper
     * @param objectMapper            Jackson（候选值 JSON 数组序列化）
     */
    public FlowPublishService(FlowDefService flowDefService,
                              WfFlowDefMapper flowDefMapper,
                              FlowValidator flowValidator,
                              FlowBpmnGenerator bpmnGenerator,
                              RepositoryService repositoryService,
                              NodeCandidateConfMapper nodeCandidateConfMapper,
                              ObjectMapper objectMapper) {
        this.flowDefService = flowDefService;
        this.flowDefMapper = flowDefMapper;
        this.flowValidator = flowValidator;
        this.bpmnGenerator = bpmnGenerator;
        this.repositoryService = repositoryService;
        this.nodeCandidateConfMapper = nodeCandidateConfMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布指定流程定义草稿：校验 → 生成 BPMN → 部署 → 写候选配置 → 版本自增。
     *
     * @param flowDefId 流程定义ID
     * @param operator  操作人工号（写入 updated_by）
     * @throws BizException WF-40400 流程定义不存在；
     *                      WF-40906 发布前校验未通过（错误明细拼入 message，且不部署）
     */
    @Transactional
    public void publish(String flowDefId, String operator) {
        long start = System.currentTimeMillis();
        log.info("[FlowPublishService.publish] 入参: flowDefId={}, operator={}", flowDefId, operator);

        // 1. 加载流程图 + 流程定义实体
        FlowGraphDTO graph = flowDefService.getGraph(flowDefId);
        WfFlowDef def = flowDefMapper.selectById(flowDefId);
        if (def == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "流程定义不存在，flowDefId=" + flowDefId);
        }

        // 2. 发布前校验：不通过则抛异常，绝不进入部署阶段
        FlowValidator.ValidationResult r = flowValidator.validate(graph, def.getBizType());
        if (!r.isOk()) {
            String detail = String.join("；", r.getErrors());
            log.warn("[FlowPublishService.publish] 校验未通过: flowDefId={}, errors={}", flowDefId, detail);
            throw new BizException(WfErrorCode.FLOW_PUBLISH_VALIDATION_FAILED.getCode(),
                    WfErrorCode.FLOW_PUBLISH_VALIDATION_FAILED.getMessage() + "：" + detail);
        }

        // 3. 影子 KEY
        String shadowKey = SHADOW_KEY_PREFIX + def.getFlowKey();

        // 4. 生成 BPMN 并序列化为 XML
        BpmnModel model = bpmnGenerator.generate(graph, shadowKey);
        byte[] xml = new BpmnXMLConverter().convertToXML(model);
        if (log.isDebugEnabled()) {
            log.debug("[FlowPublishService.publish] 生成 BPMN XML:\n{}", new String(xml, StandardCharsets.UTF_8));
        }

        // 5. 部署到 Flowable
        Deployment dep = repositoryService.createDeployment()
                .name(shadowKey)
                .addBytes(shadowKey + ".bpmn20.xml", xml)
                .deploy();

        // 6. 取部署后的流程定义，拿 procDefId / procDefKey
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(dep.getId())
                .singleResult();
        String procDefId = pd.getId();
        String procDefKey = pd.getKey();

        // 7. 整图替换候选人配置：先删旧（同影子 KEY），再按审批节点审批人规则插新
        nodeCandidateConfMapper.deleteByProcessDefinitionKey(shadowKey);
        int confCount = writeCandidateConfs(graph, shadowKey);

        // 8. 回填流程定义：发布态 + 版本自增 + 部署坐标
        def.setStatus("PUBLISHED");
        def.setVersion((def.getVersion() == null ? 0 : def.getVersion()) + 1);
        def.setDeployedProcDefKey(procDefKey);
        def.setDeployedProcDefId(procDefId);
        def.setUpdatedBy(operator);
        def.setUpdatedTime(LocalDateTime.now());
        flowDefMapper.updateById(def);

        log.info("[FlowPublishService.publish] 出参: flowDefId={}, shadowKey={}, procDefId={}, "
                        + "候选配置={}条, version={}, 耗时={}ms",
                flowDefId, shadowKey, procDefId, confCount, def.getVersion(),
                System.currentTimeMillis() - start);
    }

    /**
     * 遍历流程图中的 APPROVAL 节点，把每个节点的审批人按 approverType 分组，
     * 每组（ROLE/ORG/USER）插入一条 {@link WfNodeCandidateConf}，candidateValue 为该组
     * value 列表的 JSON 数组字符串（如 {@code ["CORP_DEPT"]}）。
     *
     * @param graph     流程图
     * @param shadowKey 影子 KEY（作为 processDefinitionKey）
     * @return 插入的候选配置条数
     */
    private int writeCandidateConfs(FlowGraphDTO graph, String shadowKey) {
        List<FlowNodeDTO> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        int count = 0;
        for (FlowNodeDTO node : nodes) {
            if (!"APPROVAL".equals(node.getNodeType())) {
                continue;
            }
            List<FlowApproverDTO> approvers = node.getApprovers();
            if (CollectionUtils.isEmpty(approvers)) {
                continue;
            }
            // 每个审批人写一条候选配置（不再按类型合并），保留各自的层级/机构信息
            for (FlowApproverDTO a : approvers) {
                WfNodeCandidateConf conf = mapApproverToConf(a, node);
                if (conf == null) {
                    continue;
                }
                conf.setId(newUuid());
                conf.setProcessDefinitionKey(shadowKey);
                conf.setNodeKey(node.getNodeKey());
                conf.setCreatedTime(LocalDateTime.now());
                conf.setUpdatedTime(LocalDateTime.now());
                nodeCandidateConfMapper.insert(conf);
                count++;
            }
        }
        return count;
    }

    /**
     * 把一个审批人 DTO 映射为运行时候选配置行（candidate_type / candidate_value / approve_org_scope / org_code）：
     * <ul>
     *   <li>LEVEL_ROLE（层级角色）→ ROLE + approve_org_scope=SELF/PARENT（角色@发起或上级机构）</li>
     *   <li>ORG_ROLE 选角色 → ROLE + org_code=固定机构（角色@该机构）</li>
     *   <li>ORG_ROLE 不选角色 → ORG（该机构任一角色=全员）</li>
     *   <li>USER / VAR → 原样</li>
     *   <li>历史 ROLE / ORG（旧设计/导入）→ 原样 + 节点级 approveOrgScope 兼容</li>
     * </ul>
     * 主值为空的审批人返回 null（跳过）。
     */
    private WfNodeCandidateConf mapApproverToConf(FlowApproverDTO a, FlowNodeDTO node) {
        String type = a.getApproverType() == null ? "" : a.getApproverType();
        String value = a.getApproverValue();
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        switch (type) {
            case "LEVEL_ROLE" -> {
                if (isBlank(value)) return null;
                conf.setCandidateType("ROLE");
                conf.setCandidateValue(toJsonArray(List.of(value)));
                conf.setApproveOrgScope(a.getOrgScope());
            }
            case "ORG_ROLE" -> {
                if (isBlank(value)) return null;
                if (!isBlank(a.getRoleCode())) {
                    conf.setCandidateType("ROLE");
                    conf.setCandidateValue(toJsonArray(List.of(a.getRoleCode())));
                    conf.setOrgCode(value);                 // 固定机构
                } else {
                    conf.setCandidateType("ORG");
                    conf.setCandidateValue(toJsonArray(List.of(value)));
                }
            }
            case "USER", "VAR", "ROLE", "ORG" -> {
                if (isBlank(value)) return null;
                conf.setCandidateType(type);
                conf.setCandidateValue(toJsonArray(List.of(value)));
                // 历史 ROLE/ORG 兼容节点级机构归属
                if ("ROLE".equals(type) || "ORG".equals(type)) {
                    conf.setApproveOrgScope(node.getApproveOrgScope());
                }
            }
            default -> {
                return null;
            }
        }
        return conf;
    }

    /** 空白判断（null 或全空格） */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 把字符串列表序列化为 JSON 数组字符串。
     *
     * @param values 值列表
     * @return JSON 数组字符串，如 {@code ["CORP_DEPT"]}
     */
    private String toJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            // 候选值均为简单字符串，理论上不会失败；兜底抛引擎异常避免静默写入坏数据
            throw new BizException(WfErrorCode.ENGINE_ERROR.getCode(),
                    "候选人配置序列化失败: " + e.getMessage());
        }
    }

    /** 生成去横线 UUID */
    private String newUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
