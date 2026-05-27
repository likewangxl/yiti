package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import com.bank.branch.platform.workflow.api.dto.CancelProcessReqDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessSubmitReqDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.api.event.ProcessWithdrawnEvent;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.ProcessStatus;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程命令服务。
 * <p>
 * 提供面向 REST 的提交流程和撤回流程能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCommandService {

    private final ProcessStartService processStartService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final CurrentUserApi currentUserApi;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 提交流程。
     *
     * @param req 提交请求
     * @return 流程启动响应
     */
    public WorkflowLaunchResp submitProcess(ProcessSubmitReqDTO req) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        String currentOrgCode = currentUserApi.getCurrentOrgCode();

        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType(req.getBizType());
        cmd.setBizId(req.getBizId());
        cmd.setBusinessKey(req.getBusinessKey());
        cmd.setProcessDefinitionKey(req.getProcessDefinitionKey());
        cmd.setStartUser(currentEmpId);
        cmd.setStartOrgId(currentOrgCode);
        cmd.setTitle(req.getTitle());
        cmd.setVariables(buildVariables(req, currentEmpId, currentOrgCode));
        return processStartService.startProcess(cmd);
    }

    /**
     * 撤回流程。
     *
     * @param processInstanceId 流程实例 ID
     * @param req 撤回请求
     */
    public void cancelProcess(String processInstanceId, CancelProcessReqDTO req) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(processInstanceId);
        if (map == null || !ProcessStatus.RUNNING.getCode().equals(map.getProcessStatus())) {
            throw new BizException(
                    WfErrorCode.PROCESS_NOT_RUNNING.getCode(),
                    WfErrorCode.PROCESS_NOT_RUNNING.getMessage());
        }

        String currentEmpId = currentUserApi.getCurrentEmpId();
        if (!currentUserApi.isSystemAdmin() && !currentEmpId.equals(map.getStartUser())) {
            throw new PermissionDeniedException("AUTH-40305", "非流程发起人不可撤回");
        }

        ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeInstance == null) {
            throw new BizException(
                    WfErrorCode.PROCESS_NOT_RUNNING.getCode(),
                    WfErrorCode.PROCESS_NOT_RUNNING.getMessage());
        }

        // 在删流程前先查当前 active task 拿 assignee，供 ProcessWithdrawnEvent 通知给下一节点审批人
        String currentAssigneeEmpId = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list().stream()
                .map(Task::getAssignee)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);

        runtimeService.deleteProcessInstance(processInstanceId, req.getReason());

        map.setProcessStatus(ProcessStatus.CANCELLED.getCode());
        map.setCurrentAssignee(null);
        map.setCandidateGroups(null);
        map.setEndTime(LocalDateTime.now());
        bizProcessMapMapper.updateById(map);
        log.info("流程撤回成功: processInstanceId={}, operator={}", processInstanceId, currentEmpId);

        // 发 ProcessWithdrawnEvent，listener 负责给 assignee 发通知（候选组未签收时 assignee=null 跳过）
        eventPublisher.publishEvent(new ProcessWithdrawnEvent(
                processInstanceId,
                map.getBusinessKey(),
                currentEmpId,
                currentAssigneeEmpId,
                req.getReason()));
    }

    private Map<String, Object> buildVariables(ProcessSubmitReqDTO req, String currentEmpId, String currentOrgCode) {
        Map<String, Object> variables = new HashMap<>();
        if (req.getVariables() != null) {
            variables.putAll(req.getVariables());
        }
        variables.put("bizType", req.getBizType());
        variables.put("bizId", req.getBizId());
        variables.put("businessKey", req.getBusinessKey());
        variables.put("startUser", currentEmpId);
        variables.put("startOrgId", currentOrgCode);
        variables.put("title", req.getTitle());
        return variables;
    }
}
