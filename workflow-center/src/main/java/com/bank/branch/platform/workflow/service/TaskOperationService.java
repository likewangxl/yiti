package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApproveReqDTO;
import com.bank.branch.platform.workflow.api.dto.RejectReqDTO;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.WfErrorCode;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.WfTaskTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务操作服务
 * <p>
 * 提供任务签收、审批通过、驳回等核心操作。
 * 所有操作会校验任务存在性和办理人权限，并同步更新 BIZ_PROCESS_MAP 映射表。
 * 旧单阶段转交（transferTask）已下线，转交统一走 {@link TaskTransferService} 的两阶段流程。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskOperationService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final BizProcessMapMapper bizProcessMapMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserApi currentUserApi;
    /** 参与机构快照写入唯一入口（D5：有具体办理人时记录，claim/approve 各记一次） */
    private final WfProcessOrgService wfProcessOrgService;
    /**
     * 转交锁校验直接注入 Mapper（不注入 TaskTransferService），避免与
     * TaskTransferService（依赖本类查询任务信息）之间形成服务→服务循环依赖。
     */
    private final WfTaskTransferMapper wfTaskTransferMapper;

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

        // 转交待认领期间，原任务对任何办理动作只读（含签收）
        ensureNotTransferLocked(taskId);

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

        // 记录参与机构快照（D5：签收即有具体办理人，source=CLAIM）
        wfProcessOrgService.record(task.getProcessInstanceId(), empId, "CLAIM");

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
        // PC 管理端会话链路：empId 取当前登录用户，并校验其为任务办理人（须已签收）
        String empId = currentUserApi.getCurrentEmpId();
        Task task = queryTaskOrThrow(taskId);
        ensureNotTransferLocked(taskId);
        verifyAssignee(task, empId);
        doApprove(task, empId, req);
    }

    /**
     * 审批通过（无会话版）：按<b>显式传入的 empId</b> 审批，<b>不校验 assignee、不要求签收</b>。
     * <p>供 callpu / SOAP 网关等无登录态链路使用（如手机端 PERF_APPR）。
     * 该 empId 是否有权审批此任务，由上游（perf 侧按候选组/角色可见性查出 taskId）保证——
     * 查不到待办 taskId 就不会调到此方法。</p>
     *
     * @param taskId 任务ID
     * @param empId  审批人工号（外部渠道认证后透传）
     * @param req    审批请求DTO
     * @throws BizException WF-40403 任务不存在
     */
    public void approveTaskByEmp(String taskId, String empId, ApproveReqDTO req) {
        Task task = queryTaskOrThrow(taskId);
        // 无会话链路（候选组任务未签收）：complete 前显式把 assignee 设为审批人 empId，
        // 否则 ACT_HI_TASKINST.ASSIGNEE_ 为 null，「已审批」查询 taskAssignee(empId).finished() 无法命中。
        taskService.setAssignee(taskId, empId);
        doApprove(task, empId, req);
    }

    /** 审批通过公共实现：加审批意见 → 完成任务（approved=true）→ 发事件。empId 仅用于留痕/事件。 */
    private void doApprove(Task task, String empId, ApproveReqDTO req) {
        String taskId = task.getId();
        // 添加审批意见
        taskService.addComment(taskId, task.getProcessInstanceId(), "APPROVE", req.getOpinion());

        // 完成任务，推动流程流转
        Map<String, Object> vars = new HashMap<>();
        if (req.getFormData() != null) {
            vars.putAll(req.getFormData());
        }
        vars.put("approved", true);
        taskService.complete(taskId, vars);

        // 记录参与机构快照（D5：审批通过即有具体办理人，source=APPROVE）
        wfProcessOrgService.record(task.getProcessInstanceId(), empId, "APPROVE");

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
        // PC 管理端会话链路：empId 取当前登录用户，并校验其为任务办理人（须已签收）
        String empId = currentUserApi.getCurrentEmpId();
        Task task = queryTaskOrThrow(taskId);
        ensureNotTransferLocked(taskId);
        verifyAssignee(task, empId);
        doReject(task, empId, req);
    }

    /**
     * 驳回（无会话版）：按<b>显式传入的 empId</b> 驳回，<b>不校验 assignee、不要求签收</b>。
     * <p>供 callpu / SOAP 网关等无登录态链路使用（如手机端 PERF_APPR）；可见性由上游 perf 侧保证。</p>
     *
     * @param taskId 任务ID
     * @param empId  审批人工号（外部渠道认证后透传）
     * @param req    驳回请求DTO（opinion 审批意见）
     * @throws BizException WF-40403 任务不存在
     */
    public void rejectTaskByEmp(String taskId, String empId, RejectReqDTO req) {
        Task task = queryTaskOrThrow(taskId);
        // 同 approveTaskByEmp：complete 前签收，保证驳回记录在「已审批」列表可见（assignee 留痕）。
        taskService.setAssignee(taskId, empId);
        doReject(task, empId, req);
    }

    /** 驳回公共实现：写意见 → 强制终止流程 → 改 biz_process_map 状态 → 发事件。empId 仅用于留痕/事件。 */
    private void doReject(Task task, String empId, RejectReqDTO req) {
        String taskId = task.getId();
        String pid = task.getProcessInstanceId();
        String opinion = req.getOpinion();

        // 1. 写驳回意见到 ACT_HI_COMMENT（必须在 deleteProcessInstance 之前；
        //    否则 task 已被 cascade 删除时 addComment 会失败）
        taskService.addComment(taskId, pid, "REJECT", opinion);

        // 2. 强制终止流程实例（直接返回申请人处，不再走后续节点）
        //    BPMN 当前未在每个 userTask 后做 ${approved == false} 分流，仅靠 complete 设
        //    approved=false 仍会被默认 sequenceFlow 带到下一节点。这里用 deleteProcessInstance
        //    显式中断，避免业务流程被误判为"通过"继续流转。
        runtimeService.deleteProcessInstance(pid, "驳回: " + opinion);

        // 3. 更新 biz_process_map 状态（ProcessCompletedListener 仅在 BPMN 自然结束时触发，
        //    deleteProcessInstance 路径不进，这里手动接管）
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(pid);
        String businessKey = null;
        if (map != null) {
            businessKey = map.getBusinessKey();
            map.setProcessStatus("CANCELLED");
            LocalDateTime now = LocalDateTime.now();
            map.setEndTime(now);
            map.setUpdatedTime(now);
            bizProcessMapMapper.updateById(map);
        }

        // 4. 手动 publish ProcessCompletedEvent outcome=REJECTED
        //    → 触发 AllocAdjustCompletedListener / TargetAdjustCompletedListener 把
        //      apply.status 改 REJECTED
        eventPublisher.publishEvent(new ProcessCompletedEvent(pid, businessKey, "REJECTED", opinion));

        // 5. 原有 TaskRejectedEvent（task 维度事件，跟流程完成事件互补）
        eventPublisher.publishEvent(new TaskRejectedEvent(taskId, pid, empId));

        log.info("任务驳回 + 流程终止: taskId={}, empId={}, pid={}, opinion={}", taskId, empId, pid, opinion);
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
     * 校验任务是否处于转交待认领锁定中，锁定则抛出 WF-40913。
     * <p>锁定判定：{@code WF_TASK_TRANSFER} 表存在该 taskId 的 PENDING_ACCEPT 记录
     * （{@link WfTaskTransferMapper#selectActiveByTaskId} 非 null）。
     * 锁定期间原办理人不可 approve/reject/claim，避免与转交流程并发冲突（详见 Task 9）。</p>
     */
    private void ensureNotTransferLocked(String taskId) {
        if (wfTaskTransferMapper.selectActiveByTaskId(taskId) != null) {
            throw new BizException(
                    WfErrorCode.TASK_TRANSFER_LOCKED.getCode(),
                    WfErrorCode.TASK_TRANSFER_LOCKED.getMessage());
        }
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
}
