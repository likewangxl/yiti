package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApproveReqDTO;
import com.bank.branch.platform.workflow.api.dto.RejectReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferReqDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.WfTaskTransferMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TaskOperationService 单元测试。
 * <p>
 * 验证任务操作服务的核心逻辑：
 * 1. 签收成功
 * 2. 签收时任务不存在抛 WF-40403
 * 3. 签收时任务已被签收抛 WF-40904
 * 4. 审批时非办理人抛 WF-40903
 * 5. 审批成功完成任务
 * 6. 转交成功变更办理人
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TaskOperationServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private BizProcessMapMapper bizProcessMapMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private WfProcessOrgService wfProcessOrgService;

    @Mock
    private WfTaskTransferMapper wfTaskTransferMapper;

    @InjectMocks
    private TaskOperationService taskOperationService;

    /**
     * 模拟任务查询链 —— 返回指定的任务（可为 null）
     */
    private void mockTaskQuery(Task result) {
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId(anyString())).thenReturn(tq);
        when(tq.singleResult()).thenReturn(result);
    }

    /**
     * 构建 mock Task 对象
     */
    private Task buildMockTask(String taskId, String processInstanceId, String assignee) {
        Task mockTask = mock(Task.class);
        lenient().when(mockTask.getId()).thenReturn(taskId);
        lenient().when(mockTask.getProcessInstanceId()).thenReturn(processInstanceId);
        lenient().when(mockTask.getAssignee()).thenReturn(assignee);
        return mockTask;
    }

    // ==================== claimTask ====================

    /**
     * 签收成功：任务存在且未被签收，签收后更新 biz_process_map 当前办理人
     */
    @Test
    void claimTask_success() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", null);
        mockTaskQuery(mockTask);

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_001");
        map.setProcessInstanceId("PID_001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        // when
        taskOperationService.claimTask("TASK_001");

        // then
        verify(taskService).claim("TASK_001", "E001");
        verify(bizProcessMapMapper).updateById(ArgumentMatchers.<BizProcessMap>argThat(m ->
                "E001".equals(m.getCurrentAssignee())
        ));
    }

    /**
     * 签收时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void claimTask_taskNotFound_throwsWf40403() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        mockTaskQuery(null);

        // when & then
        assertThatThrownBy(() -> taskOperationService.claimTask("TASK_999"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 签收时任务已被签收，应抛出 WF-40904 异常
     */
    @Test
    void claimTask_alreadyClaimed_throwsWf40904() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E002");
        mockTaskQuery(mockTask);

        // when & then
        assertThatThrownBy(() -> taskOperationService.claimTask("TASK_001"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40904");
    }

    /**
     * 签收时任务存在待认领的转交，应抛出 WF-40913 异常，且不调用 taskService.claim
     */
    @Test
    void claimTask_blockedWhenPendingTransfer() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_1", "PID_1", null);
        mockTaskQuery(mockTask);
        when(wfTaskTransferMapper.selectActiveByTaskId("TASK_1")).thenReturn(new WfTaskTransfer());

        // when & then
        assertThatThrownBy(() -> taskOperationService.claimTask("TASK_1"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40913");
        verify(taskService, never()).claim(anyString(), anyString());
    }

    // ==================== approveTask ====================

    /**
     * 审批时非任务办理人，应抛出 WF-40903 异常
     */
    @Test
    void approveTask_notAssignee_throwsWf40903() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        ApproveReqDTO req = new ApproveReqDTO("同意", Map.of());

        // when & then
        assertThatThrownBy(() -> taskOperationService.approveTask("TASK_001", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40903");
    }

    /**
     * 审批成功：验证添加评论和完成任务被调用
     */
    @Test
    void approveTask_success_completesTask() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E001");
        mockTaskQuery(mockTask);

        Map<String, Object> variables = Map.of("needCreditMeeting", "YES");
        ApproveReqDTO req = new ApproveReqDTO("同意", variables);

        // when
        taskOperationService.approveTask("TASK_001", req);

        // then
        verify(taskService).addComment("TASK_001", "PID_001", "APPROVE", "同意");
        verify(taskService).complete(eq("TASK_001"), argThat((Map<String, Object> vars) ->
                "YES".equals(vars.get("needCreditMeeting"))
                        && Boolean.TRUE.equals(vars.get("approved"))
        ));
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskApprovedEvent.class));
    }

    /**
     * 审批时任务存在待认领的转交，应抛出 WF-40913 异常，且不调用 taskService.complete
     */
    @Test
    void approve_blockedWhenPendingTransfer() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task t = buildMockTask("TASK_1", "PID_1", "E001");
        mockTaskQuery(t);
        when(wfTaskTransferMapper.selectActiveByTaskId("TASK_1")).thenReturn(new WfTaskTransfer());
        assertThatThrownBy(() -> taskOperationService.approveTask("TASK_1", new ApproveReqDTO("同意", Map.of())))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40913");
        verify(taskService, never()).complete(anyString(), anyMap());
    }

    // ==================== rejectTask ====================

    /**
     * 驳回成功：验证添加评论和完成任务（approved=false）被调用
     */
    @Test
    void rejectTask_success_terminatesProcess() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E001");
        mockTaskQuery(mockTask);

        RejectReqDTO req = new RejectReqDTO("资质不符合要求");

        // when
        taskOperationService.rejectTask("TASK_001", req);

        // then —— 当前实现：写驳回意见 + 强制终止流程实例（deleteProcessInstance，非 complete），发驳回事件
        verify(taskService).addComment("TASK_001", "PID_001", "REJECT", "资质不符合要求");
        verify(runtimeService).deleteProcessInstance(eq("PID_001"), anyString());
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskRejectedEvent.class));
    }

    /**
     * 驳回时任务存在待认领的转交，应抛出 WF-40913 异常，且不调用 runtimeService.deleteProcessInstance
     */
    @Test
    void reject_blockedWhenPendingTransfer() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task t = buildMockTask("TASK_1", "PID_1", "E001");
        mockTaskQuery(t);
        when(wfTaskTransferMapper.selectActiveByTaskId("TASK_1")).thenReturn(new WfTaskTransfer());
        assertThatThrownBy(() -> taskOperationService.rejectTask("TASK_1", new RejectReqDTO("不符合")))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40913");
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    // ==================== transferTask ====================

    /**
     * 转交成功：验证变更办理人和更新 biz_process_map
     */
    @Test
    void transferTask_success_changesAssignee() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E001");
        mockTaskQuery(mockTask);

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_001");
        map.setProcessInstanceId("PID_001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        TransferReqDTO req = new TransferReqDTO("E002", "本人出差");

        // when
        taskOperationService.transferTask("TASK_001", req);

        // then
        verify(taskService).setAssignee("TASK_001", "E002");
        verify(taskService).addComment("TASK_001", "PID_001", "TRANSFER", "本人出差");
        verify(bizProcessMapMapper).updateById(ArgumentMatchers.<BizProcessMap>argThat(m ->
                "E002".equals(m.getCurrentAssignee())
        ));
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskTransferredEvent.class));
    }

    /**
     * 转交时非任务办理人，应抛出 WF-40903 异常
     */
    @Test
    void transferTask_notAssignee_throwsWf40903() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        TransferReqDTO req = new TransferReqDTO("E002", "本人出差");

        // when & then
        assertThatThrownBy(() -> taskOperationService.transferTask("TASK_001", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40903");
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 审批时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void approveTask_taskNotFound_throwsWf40403() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        mockTaskQuery(null);

        ApproveReqDTO req = new ApproveReqDTO("同意", Map.of());

        assertThatThrownBy(() -> taskOperationService.approveTask("TASK_999", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 驳回时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void rejectTask_taskNotFound_throwsWf40403() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        mockTaskQuery(null);

        RejectReqDTO req = new RejectReqDTO("不符合");

        assertThatThrownBy(() -> taskOperationService.rejectTask("TASK_999", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 驳回时非任务办理人，应抛出 WF-40903 异常
     */
    @Test
    void rejectTask_notAssignee_throwsWf40903() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        RejectReqDTO req = new RejectReqDTO("不符合");

        assertThatThrownBy(() -> taskOperationService.rejectTask("TASK_001", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40903");
    }

    /**
     * 转交时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void transferTask_taskNotFound_throwsWf40403() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        mockTaskQuery(null);

        TransferReqDTO req = new TransferReqDTO("E002", "出差");

        assertThatThrownBy(() -> taskOperationService.transferTask("TASK_999", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    // ============ 无会话审批（approveTaskByEmp / rejectTaskByEmp，callpu/SOAP 链路）============

    /**
     * approveTaskByEmp：按显式 empId 审批，不读登录态、不校验 assignee
     * （assignee 为他人/未签收也放行；可见性由上游 perf 候选组/角色校验保证）。
     */
    @Test
    void approveTaskByEmp_noSession_completesWithoutAssigneeCheck() {
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        ApproveReqDTO req = new ApproveReqDTO("手机端同意", null);

        taskOperationService.approveTaskByEmp("TASK_001", "E001", req);

        verify(taskService).addComment("TASK_001", "PID_001", "APPROVE", "手机端同意");
        verify(taskService).complete(eq("TASK_001"), argThat((Map<String, Object> vars) ->
                Boolean.TRUE.equals(vars.get("approved"))
        ));
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskApprovedEvent.class));
        verify(currentUserApi, never()).getCurrentEmpId();
    }

    /**
     * approveTaskByEmp：任务不存在仍抛 WF-40403。
     */
    @Test
    void approveTaskByEmp_taskNotFound_throwsWf40403() {
        mockTaskQuery(null);
        ApproveReqDTO req = new ApproveReqDTO("同意", null);

        assertThatThrownBy(() -> taskOperationService.approveTaskByEmp("TASK_999", "E001", req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * rejectTaskByEmp：按显式 empId 驳回，不读登录态、不校验 assignee；终止流程 + 发驳回事件。
     */
    @Test
    void rejectTaskByEmp_noSession_rejectsWithoutAssigneeCheck() {
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(null);

        RejectReqDTO req = new RejectReqDTO("手机端驳回");

        taskOperationService.rejectTaskByEmp("TASK_001", "E001", req);

        verify(taskService).addComment("TASK_001", "PID_001", "REJECT", "手机端驳回");
        verify(runtimeService).deleteProcessInstance(eq("PID_001"), anyString());
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskRejectedEvent.class));
        verify(currentUserApi, never()).getCurrentEmpId();
    }

    /**
     * 签收时 BizProcessMap 不存在，不应抛异常（仅跳过更新）
     */
    @Test
    void claimTask_noBizProcessMap_doesNotThrow() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        Task mockTask = buildMockTask("TASK_001", "PID_001", null);
        mockTaskQuery(mockTask);
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(null);

        // 不应抛异常
        taskOperationService.claimTask("TASK_001");

        verify(taskService).claim("TASK_001", "E001");
        verify(bizProcessMapMapper, never()).updateById(any(BizProcessMap.class));
    }
}
