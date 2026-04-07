package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.service.WorkflowAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作流管理端控制器
 * <p>
 * 提供候选人配置、超时规则、节点表单配置的管理接口。
 * 所有接口需要 SYS_CONFIG + CONFIG 权限。
 * URL 对齐 spec D.1 ~ D.6。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/workflow")
@Tag(name = "工作流管理", description = "候选人配置、超时规则、节点表单配置管理")
public class WorkflowAdminController {

    private final WorkflowAdminService workflowAdminService;

    // ==================== 超时规则 (D.1 / D.2) ====================

    /**
     * 查询指定流程定义下的超时规则列表（D.1）
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 超时规则列表
     */
    @GetMapping("/timeout-rules/{processDefinitionKey}")
    @Operation(summary = "查询超时规则列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<WfTimeoutRule>> listTimeoutRules(
            @PathVariable(value = "processDefinitionKey") String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listTimeoutRules] processDefinitionKey={}", processDefinitionKey);
        List<WfTimeoutRule> list = workflowAdminService.listTimeoutRules(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 查询单条超时规则详情（D.2 GET）
     *
     * @param id 超时规则ID
     * @return 超时规则详情
     */
    @GetMapping("/timeout-rules/item/{id}")
    @Operation(summary = "查询超时规则详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<WfTimeoutRule> getTimeoutRuleById(@PathVariable(value = "id") String id) {
        log.debug("[WorkflowAdminController.getTimeoutRuleById] id={}", id);
        WfTimeoutRule rule = workflowAdminService.getTimeoutRuleById(id);
        return ResponseWrapper.success(rule);
    }

    /**
     * 更新超时规则（D.2 PUT）
     *
     * @param id       超时规则ID
     * @param warningHours 黄灯阈值（工作小时数）
     * @param timeoutHours 红灯阈值（工作小时数）
     * @return 成功响应
     */
    @PutMapping("/timeout-rules/{id}")
    @Operation(summary = "更新超时规则")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateTimeoutRule(
            @PathVariable(value = "id") String id,
            @RequestParam(value = "warningHours", required = false) Integer warningHours,
            @RequestParam(value = "timeoutHours", required = false) Integer timeoutHours) {
        log.info("[WorkflowAdminController.updateTimeoutRule] id={}, warningHours={}, timeoutHours={}",
                id, warningHours, timeoutHours);
        workflowAdminService.updateTimeoutRule(id, warningHours, timeoutHours);
        return ResponseWrapper.success();
    }

    /**
     * 新增超时规则（D.2 POST）
     *
     * @param rule 超时规则实体
     * @return 成功响应
     */
    @PostMapping("/timeout-rules")
    @Operation(summary = "新增超时规则")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveTimeoutRule(@RequestBody WfTimeoutRule rule) {
        log.info("[WorkflowAdminController.saveTimeoutRule] processDefKey={}, nodeKey={}",
                rule.getProcessDefinitionKey(), rule.getNodeKey());
        workflowAdminService.saveTimeoutRule(rule);
        return ResponseWrapper.success();
    }

    // ==================== 候选人配置 (D.3 / D.4) ====================

    /**
     * 查询指定流程定义下的候选人配置列表（D.3）
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 候选人配置列表
     */
    @GetMapping("/candidate-configs/{processDefinitionKey}")
    @Operation(summary = "查询候选人配置列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<WfNodeCandidateConf>> listCandidateConfigs(
            @PathVariable(value = "processDefinitionKey") String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listCandidateConfigs] processDefinitionKey={}", processDefinitionKey);
        List<WfNodeCandidateConf> list = workflowAdminService.listCandidateConfigs(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 查询单条候选人配置详情（D.4 GET）
     *
     * @param id 候选人配置ID
     * @return 候选人配置详情
     */
    @GetMapping("/candidate-configs/item/{id}")
    @Operation(summary = "查询候选人配置详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<WfNodeCandidateConf> getCandidateConfigById(@PathVariable(value = "id") String id) {
        log.debug("[WorkflowAdminController.getCandidateConfigById] id={}", id);
        WfNodeCandidateConf conf = workflowAdminService.getCandidateConfigById(id);
        return ResponseWrapper.success(conf);
    }

    /**
     * 更新候选人配置（D.4 PUT）
     *
     * @param id             候选人配置ID
     * @param candidateType  候选类型
     * @param candidateValue 候选值
     * @return 成功响应
     */
    @PutMapping("/candidate-configs/{id}")
    @Operation(summary = "更新候选人配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateCandidateConfig(
            @PathVariable(value = "id") String id,
            @RequestParam(value = "candidateType", required = false) String candidateType,
            @RequestParam(value = "candidateValue", required = false) String candidateValue) {
        log.info("[WorkflowAdminController.updateCandidateConfig] id={}, candidateType={}", id, candidateType);
        workflowAdminService.updateCandidateConfig(id, candidateType, candidateValue);
        return ResponseWrapper.success();
    }

    /**
     * 新增候选人配置（D.4 POST）
     *
     * @param conf 候选人配置实体
     * @return 成功响应
     */
    @PostMapping("/candidate-configs")
    @Operation(summary = "新增候选人配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveCandidateConfig(@RequestBody WfNodeCandidateConf conf) {
        log.info("[WorkflowAdminController.saveCandidateConfig] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        workflowAdminService.saveCandidateConfig(conf);
        return ResponseWrapper.success();
    }

    // ==================== 节点表单配置 (D.5 / D.6) ====================

    /**
     * 查询指定流程定义下的节点表单配置列表（D.5）
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 节点表单配置列表
     */
    @GetMapping("/node-form-confs/{processDefinitionKey}")
    @Operation(summary = "查询节点表单配置列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<WfNodeFormConf>> listNodeFormConfs(
            @PathVariable(value = "processDefinitionKey") String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listNodeFormConfs] processDefinitionKey={}", processDefinitionKey);
        List<WfNodeFormConf> list = workflowAdminService.listNodeFormConfs(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 查询单条节点表单配置详情（D.6 GET）
     *
     * @param id 节点表单配置ID
     * @return 节点表单配置详情
     */
    @GetMapping("/node-form-confs/item/{id}")
    @Operation(summary = "查询节点表单配置详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<WfNodeFormConf> getNodeFormConfById(@PathVariable(value = "id") String id) {
        log.debug("[WorkflowAdminController.getNodeFormConfById] id={}", id);
        WfNodeFormConf conf = workflowAdminService.getNodeFormConfById(id);
        return ResponseWrapper.success(conf);
    }

    /**
     * 保存（新增）节点表单配置（D.6 POST）
     *
     * @param conf 节点表单配置实体
     * @return 成功响应
     */
    @PostMapping("/node-form-confs")
    @Operation(summary = "保存节点表单配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveNodeFormConf(@RequestBody WfNodeFormConf conf) {
        log.info("[WorkflowAdminController.saveNodeFormConf] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        workflowAdminService.saveNodeFormConf(conf);
        return ResponseWrapper.success();
    }

    /**
     * 更新节点表单配置（D.6 PUT）
     *
     * @param id             节点表单配置ID
     * @param formFields     表单字段配置
     * @param editableFields 可编辑字段
     * @param requiredFields 必填字段
     * @return 成功响应
     */
    @PutMapping("/node-form-confs/{id}")
    @Operation(summary = "更新节点表单配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateNodeFormConf(
            @PathVariable(value = "id") String id,
            @RequestParam(value = "formFields", required = false) String formFields,
            @RequestParam(value = "editableFields", required = false) String editableFields,
            @RequestParam(value = "requiredFields", required = false) String requiredFields) {
        log.info("[WorkflowAdminController.updateNodeFormConf] id={}", id);
        workflowAdminService.updateNodeFormConf(id, formFields, editableFields, requiredFields);
        return ResponseWrapper.success();
    }
}
