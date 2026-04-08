package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ProcessDefinitionRespDTO;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import com.bank.branch.platform.workflow.mapper.NodeFormConfMapper;
import com.bank.branch.platform.workflow.mapper.TimeoutRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作流管理端服务
 * <p>
 * 提供候选人配置、超时规则、节点表单配置的查询与管理功能，
 * 供管理员通过 WorkflowAdminController 进行配置管理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAdminService {

    private final NodeCandidateConfMapper nodeCandidateConfMapper;
    private final TimeoutRuleMapper timeoutRuleMapper;
    private final NodeFormConfMapper nodeFormConfMapper;
    private final ProcessEngine processEngine;

    // ==================== 超时规则 (D.1 / D.2) ====================

    /**
     * 查询指定流程定义下的所有超时规则（D.1）
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 超时规则列表
     */
    public List<WfTimeoutRule> listTimeoutRules(String processDefinitionKey) {
        log.debug("[WorkflowAdminService.listTimeoutRules] processDefinitionKey={}", processDefinitionKey);
        return timeoutRuleMapper.selectByProcessDefKey(processDefinitionKey);
    }

    /**
     * 根据ID查询超时规则详情（D.2 GET）
     *
     * @param id 超时规则ID
     * @return 超时规则实体
     */
    public WfTimeoutRule getTimeoutRuleById(String id) {
        log.debug("[WorkflowAdminService.getTimeoutRuleById] id={}", id);
        WfTimeoutRule rule = timeoutRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(), "超时规则不存在: " + id);
        }
        return rule;
    }

    /**
     * 更新超时规则（D.2 PUT）
     *
     * @param id      超时规则ID
     * @param warningHours 黄灯阈值（工作小时数）
     * @param timeoutHours 红灯阈值（工作小时数）
     */
    public void updateTimeoutRule(String id, Integer warningHours, Integer timeoutHours) {
        log.info("[WorkflowAdminService.updateTimeoutRule] id={}, warningHours={}, timeoutHours={}",
                id, warningHours, timeoutHours);
        WfTimeoutRule rule = timeoutRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(), "超时规则不存在: " + id);
        }
        rule.setWarningHours(warningHours);
        rule.setTimeoutHours(timeoutHours);
        rule.setUpdatedTime(LocalDateTime.now());
        timeoutRuleMapper.updateById(rule);
    }

    /**
     * 保存超时规则（新增或更新，用于 POST 新增接口）
     *
     * @param rule 超时规则实体
     */
    public void saveTimeoutRule(WfTimeoutRule rule) {
        log.info("[WorkflowAdminService.saveTimeoutRule] processDefKey={}, nodeKey={}",
                rule.getProcessDefinitionKey(), rule.getNodeKey());
        LocalDateTime now = LocalDateTime.now();
        if (rule.getId() != null && !rule.getId().isEmpty()) {
            rule.setUpdatedTime(now);
            timeoutRuleMapper.updateById(rule);
        } else {
            rule.setId(UUID.randomUUID().toString().replace("-", ""));
            rule.setCreatedTime(now);
            rule.setUpdatedTime(now);
            timeoutRuleMapper.insert(rule);
        }
    }

    // ==================== 节点候选人配置 (D.3 / D.4) ====================

    /**
     * 查询指定流程定义下的所有候选人配置（D.3）
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 候选人配置列表
     */
    public List<WfNodeCandidateConf> listCandidateConfigs(String processDefinitionKey) {
        log.debug("[WorkflowAdminService.listCandidateConfigs] processDefinitionKey={}", processDefinitionKey);
        return nodeCandidateConfMapper.selectByProcessDefKey(processDefinitionKey);
    }

    /**
     * 根据ID查询候选人配置详情（D.4 GET）
     *
     * @param id 候选人配置ID
     * @return 候选人配置实体
     */
    public WfNodeCandidateConf getCandidateConfigById(String id) {
        log.debug("[WorkflowAdminService.getCandidateConfigById] id={}", id);
        WfNodeCandidateConf conf = nodeCandidateConfMapper.selectById(id);
        if (conf == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(), "候选人配置不存在: " + id);
        }
        return conf;
    }

    /**
     * 更新候选人配置（D.4 PUT）
     *
     * @param id            候选人配置ID
     * @param candidateType 候选类型
     * @param candidateValue 候选值（JSON数组字符串）
     */
    public void updateCandidateConfig(String id, String candidateType, String candidateValue) {
        log.info("[WorkflowAdminService.updateCandidateConfig] id={}, candidateType={}", id, candidateType);
        WfNodeCandidateConf conf = nodeCandidateConfMapper.selectById(id);
        if (conf == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(), "候选人配置不存在: " + id);
        }
        conf.setCandidateType(candidateType);
        conf.setCandidateValue(candidateValue);
        conf.setUpdatedTime(LocalDateTime.now());
        nodeCandidateConfMapper.updateById(conf);
    }

    /**
     * 保存候选人配置（新增或更新，用于 POST 新增接口）
     *
     * @param conf 候选人配置实体
     */
    public void saveCandidateConfig(WfNodeCandidateConf conf) {
        log.info("[WorkflowAdminService.saveCandidateConfig] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        LocalDateTime now = LocalDateTime.now();
        if (conf.getId() != null && !conf.getId().isEmpty()) {
            conf.setUpdatedTime(now);
            nodeCandidateConfMapper.updateById(conf);
        } else {
            conf.setId(UUID.randomUUID().toString().replace("-", ""));
            conf.setCreatedTime(now);
            conf.setUpdatedTime(now);
            nodeCandidateConfMapper.insert(conf);
        }
    }

    // ==================== 节点表单配置 (D.5 / D.6) ====================

    /**
     * 查询指定流程定义下的所有节点表单配置（D.5）
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 节点表单配置列表
     */
    public List<WfNodeFormConf> listNodeFormConfs(String processDefinitionKey) {
        log.debug("[WorkflowAdminService.listNodeFormConfs] processDefinitionKey={}", processDefinitionKey);
        return nodeFormConfMapper.selectByProcessDefKey(processDefinitionKey);
    }

    /**
     * 根据ID查询节点表单配置详情（D.6 GET）
     *
     * @param id 节点表单配置ID
     * @return 节点表单配置实体
     */
    public WfNodeFormConf getNodeFormConfById(String id) {
        log.debug("[WorkflowAdminService.getNodeFormConfById] id={}", id);
        WfNodeFormConf conf = nodeFormConfMapper.selectById(id);
        if (conf == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(), "节点表单配置不存在: " + id);
        }
        return conf;
    }

    /**
     * 保存节点表单配置（新增或更新，用于 POST 新增接口）
     *
     * @param conf 节点表单配置实体
     */
    public void saveNodeFormConf(WfNodeFormConf conf) {
        log.info("[WorkflowAdminService.saveNodeFormConf] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        LocalDateTime now = LocalDateTime.now();
        if (conf.getId() != null && !conf.getId().isEmpty()) {
            conf.setUpdatedTime(now);
            nodeFormConfMapper.updateById(conf);
        } else {
            conf.setId(UUID.randomUUID().toString().replace("-", ""));
            conf.setCreatedTime(now);
            conf.setUpdatedTime(now);
            nodeFormConfMapper.insert(conf);
        }
    }

    /**
     * 更新节点表单配置（D.6 PUT）
     *
     * @param id              节点表单配置ID
     * @param formFields      表单字段配置（JSON数组字符串）
     * @param editableFields  可编辑字段列表（JSON数组字符串）
     * @param requiredFields  必填字段列表（JSON数组字符串）
     */
    public void updateNodeFormConf(String id, String formFields, String editableFields, String requiredFields) {
        log.info("[WorkflowAdminService.updateNodeFormConf] id={}", id);
        WfNodeFormConf conf = nodeFormConfMapper.selectById(id);
        if (conf == null) {
            throw new BizException(WfErrorCode.RESOURCE_NOT_FOUND.getCode(), "节点表单配置不存在: " + id);
        }
        conf.setFormFields(formFields);
        conf.setEditableFields(editableFields);
        conf.setRequiredFields(requiredFields);
        conf.setUpdatedTime(LocalDateTime.now());
        nodeFormConfMapper.updateById(conf);
    }

    // ==================== 流程定义 (D.7) ====================

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 获取流程定义列表（D.7）
     * 从 Flowable RepositoryService 读取已部署的流程定义列表。
     *
     * @param active 是否仅查询已激活的流程定义
     * @return 流程定义信息列表
     */
    public List<ProcessDefinitionRespDTO> listProcessDefinitions(boolean active) {
        log.debug("[WorkflowAdminService.listProcessDefinitions] active={}", active);
        RepositoryService repositoryService = processEngine.getRepositoryService();
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
        if (active) {
            query.active();
        }
        List<ProcessDefinition> definitions = query.orderByProcessDefinitionKey().asc().list();
        return definitions.stream().map(def -> {
            ProcessDefinitionRespDTO dto = new ProcessDefinitionRespDTO();
            dto.setProcessDefinitionId(def.getId());
            dto.setProcessDefinitionKey(def.getKey());
            dto.setProcessDefinitionName(def.getName());
            dto.setVersion(def.getVersion());
            dto.setDeploymentId(def.getDeploymentId());
            dto.setSuspended(def.isSuspended());
            dto.setDescription(def.getDescription());
            // 从 DeploymentQuery 获取部署时间
            if (def.getDeploymentId() != null) {
                try {
                    var deploymentList = repositoryService.createDeploymentQuery()
                            .deploymentId(def.getDeploymentId()).list();
                    if (deploymentList != null && !deploymentList.isEmpty()) {
                        var deployment = deploymentList.get(0);
                        if (deployment.getDeploymentTime() != null) {
                            dto.setDeploymentTime(
                                    deployment.getDeploymentTime().toInstant()
                                            .atZone(ZoneId.systemDefault())
                                            .format(ISO_FORMATTER));
                        }
                    }
                } catch (Exception e) {
                    log.warn("[WorkflowAdminService] 获取部署时间失败, deploymentId={}", def.getDeploymentId(), e);
                }
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
