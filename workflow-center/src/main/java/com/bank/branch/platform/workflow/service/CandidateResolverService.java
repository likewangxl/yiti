package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程节点候选人解析服务。
 * <p>
 * 根据流程定义KEY和节点KEY查询候选人配置，
 * 解析 JSON 格式的 candidateValue，并根据 candidateType 添加前缀。
 * 支持 ROLE、ORG、USER 三种候选类型。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateResolverService {

    private final NodeCandidateConfMapper nodeCandidateConfMapper;

    /** JSON 解析器，用于解析 candidateValue 中的 JSON 数组 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** candidateValue JSON 数组的类型引用 */
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    /**
     * 解析指定流程节点的候选人列表。
     * <p>
     * 查询该节点的所有候选人配置，解析 JSON 数组并根据类型添加前缀：
     * ROLE → "ROLE:value"，ORG → "ORG:value"，USER → "USER:value"。
     * 多条配置的结果合并为一个列表返回。
     * 若某条配置的 JSON 解析失败，记录错误日志并跳过该条配置。
     * </p>
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @return 带前缀的候选人标识列表，无配置时返回空列表
     */
    public List<String> resolveCandidates(String processDefinitionKey, String nodeKey) {
        log.debug("[CandidateResolverService.resolveCandidates] processDefinitionKey={}, nodeKey={}",
                processDefinitionKey, nodeKey);

        List<WfNodeCandidateConf> configs = nodeCandidateConfMapper
                .selectByProcessDefKeyAndNodeKey(processDefinitionKey, nodeKey);

        if (configs == null || configs.isEmpty()) {
            log.debug("[CandidateResolverService.resolveCandidates] 未找到候选人配置，返回空列表");
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (WfNodeCandidateConf conf : configs) {
            List<String> values = parseCandidateValue(conf.getCandidateValue());
            String prefix = conf.getCandidateType() + ":";
            for (String value : values) {
                result.add(prefix + value);
            }
        }

        log.debug("[CandidateResolverService.resolveCandidates] 解析完成，共 {} 个候选人", result.size());
        return result;
    }

    /**
     * 解析指定流程节点的「审批机构归属」配置（SELF=本机构 / PARENT=上级机构 / null=不判断）。
     * <p>
     * 取该节点候选配置中首个非空 approveOrgScope（同一节点各候选行该值一致，由发布时统一写入）。
     * 供 {@code TaskAssignmentListener} 决定是否按发起人机构/上级机构过滤候选。
     * </p>
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @return SELF / PARENT，未配置时返回 null
     */
    public String resolveApproveOrgScope(String processDefinitionKey, String nodeKey) {
        List<WfNodeCandidateConf> configs = nodeCandidateConfMapper
                .selectByProcessDefKeyAndNodeKey(processDefinitionKey, nodeKey);
        if (configs == null) {
            return null;
        }
        return configs.stream()
                .map(WfNodeCandidateConf::getApproveOrgScope)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析「每个候选人各自的审批机构归属」映射：带前缀候选标识（如 {@code ROLE:CORP_PERF_REV}）→
     * 该审批人配置的 {@code approve_org_scope}（SELF / PARENT）。
     * <p>
     * 仅收录<b>显式配置了机构归属</b>的审批人；未配置机构归属的审批人<b>不在</b> map 中
     * （调用方据此对其不做机构过滤、整组角色作为候选组，避免同一节点混合机构归属时
     * 把"不限机构"的审批人也按别的审批人的 SELF/PARENT 误过滤——
     * 这是 {@link #resolveApproveOrgScope} 节点级单一取值无法表达的逐行语义）。
     * </p>
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @return 带前缀候选标识 → SELF/PARENT 的映射（保持配置顺序），无则空 map
     */
    public Map<String, String> resolveCandidateScopeMap(String processDefinitionKey, String nodeKey) {
        List<WfNodeCandidateConf> configs = nodeCandidateConfMapper
                .selectByProcessDefKeyAndNodeKey(processDefinitionKey, nodeKey);
        Map<String, String> scopeByCandidate = new LinkedHashMap<>();
        if (configs == null) {
            return scopeByCandidate;
        }
        for (WfNodeCandidateConf conf : configs) {
            String scope = conf.getApproveOrgScope();
            if (scope == null || scope.isBlank()) {
                continue; // 不限机构的审批人不收录
            }
            String prefix = conf.getCandidateType() + ":";
            for (String value : parseCandidateValue(conf.getCandidateValue())) {
                scopeByCandidate.put(prefix + value, scope);
            }
        }
        return scopeByCandidate;
    }

    /**
     * 解析 candidateValue JSON 数组字符串为字符串列表。
     *
     * @param candidateValue JSON 数组字符串，如 ["CUST_MANAGER","TEAM_LEAD"]
     * @return 解析后的字符串列表，解析失败时返回空列表
     */
    private List<String> parseCandidateValue(String candidateValue) {
        try {
            return OBJECT_MAPPER.readValue(candidateValue, STRING_LIST_TYPE);
        } catch (Exception e) {
            log.error("[CandidateResolverService.parseCandidateValue] JSON 解析失败，candidateValue={}",
                    candidateValue, e);
            return Collections.emptyList();
        }
    }
}
