package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.service.WorkflowAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作流管理端控制器
 * <p>
 * 提供候选人配置和超时规则的管理接口。
 * 所有接口需要 SYS_CONFIG + CONFIG 权限。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/workflow")
@Tag(name = "工作流管理", description = "候选人配置与超时规则管理")
public class WorkflowAdminController {

    private final WorkflowAdminService workflowAdminService;

    /**
     * 查询指定流程定义下的候选人配置列表
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 候选人配置列表
     */
    @GetMapping("/candidate-configs")
    @Operation(summary = "查询候选人配置列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<WfNodeCandidateConf>> listCandidateConfigs(
            @RequestParam(value = "processDefinitionKey") String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listCandidateConfigs] processDefinitionKey={}", processDefinitionKey);
        List<WfNodeCandidateConf> list = workflowAdminService.listCandidateConfigs(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 保存候选人配置（新增或更新）
     *
     * @param conf 候选人配置实体
     * @return 成功响应
     */
    @PostMapping("/candidate-configs")
    @Operation(summary = "保存候选人配置")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveCandidateConfig(@RequestBody WfNodeCandidateConf conf) {
        log.info("[WorkflowAdminController.saveCandidateConfig] processDefKey={}, nodeKey={}",
                conf.getProcessDefinitionKey(), conf.getNodeKey());
        workflowAdminService.saveCandidateConfig(conf);
        return ResponseWrapper.success();
    }

    /**
     * 查询指定流程定义下的超时规则列表
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 超时规则列表
     */
    @GetMapping("/timeout-rules")
    @Operation(summary = "查询超时规则列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<List<WfTimeoutRule>> listTimeoutRules(
            @RequestParam(value = "processDefinitionKey") String processDefinitionKey) {
        log.debug("[WorkflowAdminController.listTimeoutRules] processDefinitionKey={}", processDefinitionKey);
        List<WfTimeoutRule> list = workflowAdminService.listTimeoutRules(processDefinitionKey);
        return ResponseWrapper.success(list);
    }

    /**
     * 保存超时规则（新增或更新）
     *
     * @param rule 超时规则实体
     * @return 成功响应
     */
    @PostMapping("/timeout-rules")
    @Operation(summary = "保存超时规则")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> saveTimeoutRule(@RequestBody WfTimeoutRule rule) {
        log.info("[WorkflowAdminController.saveTimeoutRule] processDefKey={}, nodeKey={}",
                rule.getProcessDefinitionKey(), rule.getNodeKey());
        workflowAdminService.saveTimeoutRule(rule);
        return ResponseWrapper.success();
    }
}
