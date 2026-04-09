package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.NodeCandidateRespDTO;
import com.bank.branch.platform.workflow.api.dto.NodeCandidateUpdateReqDTO;
import com.bank.branch.platform.workflow.api.dto.NodeFormRespDTO;
import com.bank.branch.platform.workflow.api.dto.NodeFormUpdateReqDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDefinitionRespDTO;
import com.bank.branch.platform.workflow.api.dto.TimeoutRuleRespDTO;
import com.bank.branch.platform.workflow.api.dto.TimeoutRuleUpdateReqDTO;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.service.WorkflowAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
     * 查询超时规则列表（D.1）
     * 设计文档: GET /api/admin/workflow/timeout-rules?processDefinitionKey=xxx
     *
     * @param processDefinitionKey 流程定义KEY (可选)
     * @return 超时规则列表
     */
    @GetMapping("/timeout-rules")
    @Operation(summary = "查询超时规则列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<TimeoutRuleRespDTO>> listTimeoutRules(
            @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listTimeoutRules] processDefinitionKey={}", processDefinitionKey);
        List<TimeoutRuleRespDTO> list = workflowAdminService.listTimeoutRulesResp(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 查询单条超时规则详情
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
     * 设计文档: PUT /api/admin/workflow/timeout-rules/{id} with JSON body
     *
     * @param id   超时规则ID
     * @param req  更新请求 (warningHours, timeoutHours)
     * @return 成功响应
     */
    @PutMapping("/timeout-rules/{id}")
    @Operation(summary = "更新超时规则")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateTimeoutRule(
            @PathVariable(value = "id") String id,
            @Valid @RequestBody TimeoutRuleUpdateReqDTO req) {
        log.info("[WorkflowAdminController.updateTimeoutRule] id={}, warningHours={}, timeoutHours={}",
                id, req.getWarningHours(), req.getTimeoutHours());
        workflowAdminService.updateTimeoutRule(id, req.getWarningHours(), req.getTimeoutHours());
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
     * 查询节点候选人配置列表（D.3）
     * 设计文档: GET /api/admin/workflow/node-candidates?processDefinitionKey=xxx
     *
     * @param processDefinitionKey 流程定义KEY (可选)
     * @return 候选人配置列表
     */
    @GetMapping("/node-candidates")
    @Operation(summary = "查询节点候选人配置列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<NodeCandidateRespDTO>> listNodeCandidates(
            @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listNodeCandidates] processDefinitionKey={}", processDefinitionKey);
        List<NodeCandidateRespDTO> list = workflowAdminService.listCandidateConfigsResp(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 查询单条候选人配置详情
     */
    @GetMapping("/node-candidates/item/{id}")
    @Operation(summary = "查询候选人配置详情")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<WfNodeCandidateConf> getCandidateConfigById(@PathVariable(value = "id") String id) {
        log.debug("[WorkflowAdminController.getCandidateConfigById] id={}", id);
        WfNodeCandidateConf conf = workflowAdminService.getCandidateConfigById(id);
        return ResponseWrapper.success(conf);
    }

    /**
     * 更新候选人配置（D.4 PUT）
     * 设计文档: PUT /api/admin/workflow/node-candidates/{id} with JSON body
     *
     * @param id   候选人配置ID
     * @param req  更新请求 (candidateType, candidateValue)
     * @return 成功响应
     */
    @PutMapping("/node-candidates/{id}")
    @Operation(summary = "更新候选人配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateNodeCandidate(
            @PathVariable(value = "id") String id,
            @Valid @RequestBody NodeCandidateUpdateReqDTO req) {
        log.info("[WorkflowAdminController.updateNodeCandidate] id={}, candidateType={}", id, req.getCandidateType());
        workflowAdminService.updateCandidateConfig(id, req.getCandidateType(), String.join(",", req.getCandidateValue()));
        return ResponseWrapper.success();
    }

    /**
     * 新增候选人配置（D.4 POST）
     *
     * @param conf 候选人配置实体
     * @return 成功响应
     */
    @PostMapping("/node-candidates")
    @Operation(summary = "新增候选人配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveNodeCandidate(@RequestBody WfNodeCandidateConf conf) {
        log.info("[WorkflowAdminController.saveNodeCandidate] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        workflowAdminService.saveCandidateConfig(conf);
        return ResponseWrapper.success();
    }

    // ==================== 节点表单配置 (D.5 / D.6) ====================

    /**
     * 查询节点表单配置列表（D.5）
     * 设计文档: GET /api/admin/workflow/node-forms?processDefinitionKey=xxx
     *
     * @param processDefinitionKey 流程定义KEY (可选)
     * @return 节点表单配置列表
     */
    @GetMapping("/node-forms")
    @Operation(summary = "查询节点表单配置列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<NodeFormRespDTO>> listNodeForms(
            @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listNodeForms] processDefinitionKey={}", processDefinitionKey);
        List<NodeFormRespDTO> list = workflowAdminService.listNodeFormConfsResp(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 查询单条节点表单配置详情
     */
    @GetMapping("/node-forms/item/{id}")
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
    @PostMapping("/node-forms")
    @Operation(summary = "保存节点表单配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveNodeForm(@RequestBody WfNodeFormConf conf) {
        log.info("[WorkflowAdminController.saveNodeForm] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        workflowAdminService.saveNodeFormConf(conf);
        return ResponseWrapper.success();
    }

    /**
     * 更新节点表单配置（D.6 PUT）
     * 设计文档: PUT /api/admin/workflow/node-forms/{id} with JSON body
     *
     * @param id   节点表单配置ID
     * @param req  更新请求 (formFields, editableFields, requiredFields)
     * @return 成功响应
     */
    @PutMapping("/node-forms/{id}")
    @Operation(summary = "更新节点表单配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateNodeForm(
            @PathVariable(value = "id") String id,
            @Valid @RequestBody NodeFormUpdateReqDTO req) {
        log.info("[WorkflowAdminController.updateNodeForm] id={}", id);
        workflowAdminService.updateNodeFormConf(id,
                req.getFormFields() != null ? req.getFormFields().toString() : null,
                req.getEditableFields() != null ? req.getEditableFields().toString() : null,
                req.getRequiredFields() != null ? req.getRequiredFields().toString() : null);
        return ResponseWrapper.success();
    }

    // ==================== 流程定义 (D.7) ====================

    /**
     * 获取流程定义列表（D.7）
     * 设计文档: GET /api/admin/workflow/process-definitions
     *
     * @param active 是否仅查询已激活的流程定义，默认 true
     * @return 流程定义列表
     */
    @GetMapping("/process-definitions")
    @Operation(summary = "获取流程定义列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<ProcessDefinitionRespDTO>> listProcessDefinitions(
            @RequestParam(value = "active", defaultValue = "true") boolean active) {
        log.debug("[WorkflowAdminController.listProcessDefinitions] active={}", active);
        List<ProcessDefinitionRespDTO> list = workflowAdminService.listProcessDefinitions(active);
        return ResponseWrapper.success(list);
    }
}
