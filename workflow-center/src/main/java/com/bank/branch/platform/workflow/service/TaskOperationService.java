package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApproveReqDTO;
import com.bank.branch.platform.workflow.api.dto.RejectReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferReqDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务操作服务
 * <p>
 * 提供任务签收、审批通过、驳回、转交等核心操作。
 * 所有操作会校验任务存在性和办理人权限，并同步更新 BIZ_PROCESS_MAP 映射表。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskOperationService {

    private final TaskService taskService;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserApi currentUserApi;

    /**
     * 签收任务
     * <p>
     * 候选组用户签收未分配的任务，签收后成为任务唯一办理人。
     * </p>
     *
     * @param taskId 任务ID
     * @throws BizException WF-40403 任务不存在；WF-40904 任务已被签收
     */
    public void claimTask(String taskId) {
        String empId = currentUserApi.getCurrentEmpId();
        // 查询任务，不存在则抛异常
        Task task = queryTaskOrThrow(taskId);

        // 检查任务是否已被签收
        if (task.getAssignee() != null) {
            throw new BizException(
                    WfErrorCode.TASK_ALREADY_CLAIMED.getCode(),
                    WfErrorCode.TASK_ALREADY_CLAIMED.getMessage());
        }

        // 执行签收
        taskService.claim(taskId, empId);

        // 更新 BIZ_PROCESS_MAP 当前办理人
        updateCurrentAssignee(task.getProcessInstanceId(), empId);

        log.info("任务签收成功: taskId={}, empId={}", taskId, empId);
    }

    /**
     * 审批通过任务
     * <p>
     * 当前办理人审批通过任务，添加审批意见并完成任务流转。
     * </p>
     *
     * @param taskId 任务ID
     * @param req    审批请求DTO
     * @throws BizException WF-40403 任务不存在；WF-40903 非任务办理人
     */
    public void approveTask(String taskId, ApproveReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        // 查询任务并校验办理人
        Task task = queryTaskOrThrow(taskId);
        verifyAssignee(task, empId);

        // 添加审批意见
        taskService.addComment(taskId, task.getProcessInstanceId(), "APPROVE", req.getOpinion());

        // 完成任务，推动流程流转
        Map<String, Object> vars = new HashMap<>();
        if (req.getFormData() != null) {
            vars.putAll(req.getFormData());
        }
        vars.put("approved", true);
        taskService.complete(taskId, vars);

        // 发布事件
        eventPublisher.publishEvent(new TaskApprovedEvent(taskId, task.getProcessInstanceId(), empId));

        log.info("任务审批通过: taskId={}, empId={}", taskId, empId);
    }

    /**
     * 驳回任务
     * <p>
     * 当前办理人驳回任务，设置 approved=false 流程变量并完成任务。
     * </p>
     *
     * @param taskId 任务ID
     * @param req    驳回请求DTO（opinion 审批意见）
     * @throws BizException WF-40403 任务不存在；WF-40903 非任务办理人
     */
    public void rejectTask(String taskId, RejectReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        // 查询任务并校验办理人
        Task task = queryTaskOrThrow(taskId);
        verifyAssignee(task, empId);

        // 设置驳回变量
        Map<String, Object> vars = Map.of("approved", false);

        // 添加驳回意见
        taskService.addComment(taskId, task.getProcessInstanceId(), "REJECT", req.getOpinion());

        // 完成任务（带驳回变量）
        taskService.complete(taskId, vars);

        // 发布事件
        eventPublisher.publishEvent(new TaskRejectedEvent(taskId, task.getProcessInstanceId(), empId));

        log.info("任务驳回: taskId={}, empId={}, opinion={}", taskId, empId, req.getOpinion());
    }

    /**
     * 转交任务
     * <p>
     * 当前办理人将任务转交给其他人员，变更任务办理人并同步更新映射表。
     * </p>
     *
     * @param taskId 任务ID
     * @param req    转交请求DTO（targetEmpId 接收人，reason 转交原因）
     * @throws BizException WF-40403 任务不存在；WF-40903 非任务办理人
     */
    public void transferTask(String taskId, TransferReqDTO req) {
        String fromEmpId = currentUserApi.getCurrentEmpId();
        String toEmpId = req.getTargetEmpId();
        // 查询任务并校验办理人
        Task task = queryTaskOrThrow(taskId);
        verifyAssignee(task, fromEmpId);

        // 变更办理人
        taskService.setAssignee(taskId, toEmpId);

        // 添加转交备注
        taskService.addComment(taskId, task.getProcessInstanceId(), "TRANSFER", req.getReason());

        // 更新 BIZ_PROCESS_MAP 当前办理人
        updateCurrentAssignee(task.getProcessInstanceId(), toEmpId);

        // 发布事件
        eventPublisher.publishEvent(new TaskTransferredEvent(taskId, task.getProcessInstanceId(), fromEmpId, toEmpId));

        log.info("任务转交: taskId={}, from={}, to={}, reason={}", taskId, fromEmpId, toEmpId, req.getReason());
    }

    // ==================== 私有方法 ====================

    /**
     * 查询任务，不存在则抛出 WF-40403
     */
    private Task queryTaskOrThrow(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            throw new BizException(
                    WfErrorCode.TASK_NOT_FOUND.getCode(),
                    WfErrorCode.TASK_NOT_FOUND.getMessage());
        }
        return task;
    }

    /**
     * 校验当前用户是否为任务办理人，不是则抛出 WF-40903
     */
    private void verifyAssignee(Task task, String empId) {
        if (!empId.equals(task.getAssignee())) {
            throw new BizException(
                    WfErrorCode.NOT_TASK_ASSIGNEE.getCode(),
                    WfErrorCode.NOT_TASK_ASSIGNEE.getMessage());
        }
    }

    /**
     * 更新 BIZ_PROCESS_MAP 表的当前办理人字段。
     * 如果找不到映射记录，仅记录警告日志，不抛异常。
     */
    private void updateCurrentAssignee(String processInstanceId, String empId) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(processInstanceId);
        if (map == null) {
            log.warn("未找到流程实例 {} 对应的 BIZ_PROCESS_MAP 记录，跳过更新当前办理人", processInstanceId);
            return;
        }
        map.setCurrentAssignee(empId);
        bizProcessMapMapper.updateById(map);
    }

    // ==================== 事件定义 ====================

    /** 任务审批通过事件 */
    public record TaskApprovedEvent(String taskId, String processInstanceId, String empId) {}

    /** 任务驳回事件 */
    public record TaskRejectedEvent(String taskId, String processInstanceId, String empId) {}

    /** 任务转交事件 */
    public record TaskTransferredEvent(String taskId, String processInstanceId, String fromEmpId, String toEmpId) {}
}
