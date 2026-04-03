package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.TimeoutRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 工作流管理端服务
 * <p>
 * 提供候选人配置和超时规则的查询与保存功能，
 * 供管理员通过 WorkflowAdminController 进行配置管理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAdminService {

    private final NodeCandidateConfMapper nodeCandidateConfMapper;
    private final TimeoutRuleMapper timeoutRuleMapper;

    /**
     * 查询指定流程定义下的所有候选人配置
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 候选人配置列表
     */
    public List<WfNodeCandidateConf> listCandidateConfigs(String processDefinitionKey) {
        log.debug("[WorkflowAdminService.listCandidateConfigs] processDefinitionKey={}", processDefinitionKey);
        return nodeCandidateConfMapper.selectByProcessDefKey(processDefinitionKey);
    }

    /**
     * 保存候选人配置（新增或更新）
     * <p>
     * 有 id 时执行更新，无 id 时生成 UUID 并新增。
     * </p>
     *
     * @param conf 候选人配置实体
     */
    public void saveCandidateConfig(WfNodeCandidateConf conf) {
        log.info("[WorkflowAdminService.saveCandidateConfig] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        LocalDateTime now = LocalDateTime.now();
        if (conf.getId() != null && !conf.getId().isEmpty()) {
            // 更新
            conf.setUpdatedTime(now);
            nodeCandidateConfMapper.updateById(conf);
        } else {
            // 新增
            conf.setId(UUID.randomUUID().toString().replace("-", ""));
            conf.setCreatedTime(now);
            conf.setUpdatedTime(now);
            nodeCandidateConfMapper.insert(conf);
        }
    }

    /**
     * 查询指定流程定义下的所有超时规则
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 超时规则列表
     */
    public List<WfTimeoutRule> listTimeoutRules(String processDefinitionKey) {
        log.debug("[WorkflowAdminService.listTimeoutRules] processDefinitionKey={}", processDefinitionKey);
        return timeoutRuleMapper.selectByProcessDefKey(processDefinitionKey);
    }

    /**
     * 保存超时规则（新增或更新）
     * <p>
     * 有 id 时执行更新，无 id 时生成 UUID 并新增。
     * </p>
     *
     * @param rule 超时规则实体
     */
    public void saveTimeoutRule(WfTimeoutRule rule) {
        log.info("[WorkflowAdminService.saveTimeoutRule] processDefKey={}, nodeKey={}",
                rule.getProcessDefinitionKey(), rule.getNodeKey());
        LocalDateTime now = LocalDateTime.now();
        if (rule.getId() != null && !rule.getId().isEmpty()) {
            // 更新
            rule.setUpdatedTime(now);
            timeoutRuleMapper.updateById(rule);
        } else {
            // 新增
            rule.setId(UUID.randomUUID().toString().replace("-", ""));
            rule.setCreatedTime(now);
            rule.setUpdatedTime(now);
            timeoutRuleMapper.insert(rule);
        }
    }
}
