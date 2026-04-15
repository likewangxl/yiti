package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.CancelProcessReqDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessSubmitReqDTO;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.service.ProcessCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程命令控制器。
 * <p>
 * 提供提交流程与撤回流程的 REST 入口，便于真实环境联调与 curl 验证。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/processes")
@Tag(name = "流程命令", description = "提交流程与撤回流程")
public class ProcessCommandController {

    private final ProcessCommandService processCommandService;

    /**
     * 提交流程。
     *
     * @param req 提交流程请求
     * @return 流程启动响应
     */
    @PostMapping("/submit")
    @Operation(summary = "提交流程")
    public ResponseWrapper<WorkflowLaunchResp> submitProcess(@Valid @RequestBody ProcessSubmitReqDTO req) {
        log.info("[ProcessCommandController.submitProcess] businessKey={}, processDefinitionKey={}",
                req.getBusinessKey(), req.getProcessDefinitionKey());
        return ResponseWrapper.success(processCommandService.submitProcess(req));
    }

    /**
     * 撤回流程。
     *
     * @param processInstanceId 流程实例 ID
     * @param req 撤回请求
     * @return 成功响应
     */
    @PostMapping("/{processInstanceId}/cancel")
    @Operation(summary = "撤回流程")
    public ResponseWrapper<Void> cancelProcess(@PathVariable String processInstanceId,
                                               @Valid @RequestBody CancelProcessReqDTO req) {
        log.info("[ProcessCommandController.cancelProcess] processInstanceId={}", processInstanceId);
        processCommandService.cancelProcess(processInstanceId, req);
        return ResponseWrapper.success();
    }
}
