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
import java.util.List;

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
