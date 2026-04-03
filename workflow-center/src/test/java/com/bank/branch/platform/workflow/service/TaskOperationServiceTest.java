package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
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
        Task mockTask = buildMockTask("TASK_001", "PID_001", null);
        mockTaskQuery(mockTask);

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_001");
        map.setProcessInstanceId("PID_001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        // when
        taskOperationService.claimTask("TASK_001", "E001");

        // then
        verify(taskService).claim("TASK_001", "E001");
        verify(bizProcessMapMapper).updateById(argThat(m ->
                "E001".equals(m.getCurrentAssignee())
        ));
    }

    /**
     * 签收时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void claimTask_taskNotFound_throwsWf40403() {
        // given
        mockTaskQuery(null);

        // when & then
        assertThatThrownBy(() -> taskOperationService.claimTask("TASK_999", "E001"))
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
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E002");
        mockTaskQuery(mockTask);

        // when & then
        assertThatThrownBy(() -> taskOperationService.claimTask("TASK_001", "E001"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40904");
    }

    // ==================== approveTask ====================

    /**
     * 审批时非任务办理人，应抛出 WF-40903 异常
     */
    @Test
    void approveTask_notAssignee_throwsWf40903() {
        // given
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        // when & then
        assertThatThrownBy(() -> taskOperationService.approveTask(
                "TASK_001", "E001", Map.of(), "同意"))
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
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E001");
        mockTaskQuery(mockTask);

        Map<String, Object> variables = Map.of("needCreditMeeting", "YES");

        // when
        taskOperationService.approveTask("TASK_001", "E001", variables, "同意");

        // then
        verify(taskService).addComment("TASK_001", "PID_001", "APPROVE", "同意");
        verify(taskService).complete("TASK_001", variables);
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskApprovedEvent.class));
    }

    // ==================== rejectTask ====================

    /**
     * 驳回成功：验证添加评论和完成任务（approved=false）被调用
     */
    @Test
    void rejectTask_success_completesWithReject() {
        // given
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E001");
        mockTaskQuery(mockTask);

        // when
        taskOperationService.rejectTask("TASK_001", "E001", "资质不符合要求");

        // then
        verify(taskService).addComment("TASK_001", "PID_001", "REJECT", "资质不符合要求");
        verify(taskService).complete(eq("TASK_001"), argThat((Map<String, Object> vars) ->
                Boolean.FALSE.equals(vars.get("approved"))
        ));
        verify(eventPublisher).publishEvent(any(TaskOperationService.TaskRejectedEvent.class));
    }

    // ==================== transferTask ====================

    /**
     * 转交成功：验证变更办理人和更新 biz_process_map
     */
    @Test
    void transferTask_success_changesAssignee() {
        // given
        Task mockTask = buildMockTask("TASK_001", "PID_001", "E001");
        mockTaskQuery(mockTask);

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_001");
        map.setProcessInstanceId("PID_001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        // when
        taskOperationService.transferTask("TASK_001", "E001", "E002", "本人出差");

        // then
        verify(taskService).setAssignee("TASK_001", "E002");
        verify(taskService).addComment("TASK_001", "PID_001", "TRANSFER", "本人出差");
        verify(bizProcessMapMapper).updateById(argThat(m ->
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
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        // when & then
        assertThatThrownBy(() -> taskOperationService.transferTask(
                "TASK_001", "E001", "E002", "本人出差"))
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
        mockTaskQuery(null);

        assertThatThrownBy(() -> taskOperationService.approveTask(
                "TASK_999", "E001", Map.of(), "同意"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 驳回时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void rejectTask_taskNotFound_throwsWf40403() {
        mockTaskQuery(null);

        assertThatThrownBy(() -> taskOperationService.rejectTask(
                "TASK_999", "E001", "不符合"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 驳回时非任务办理人，应抛出 WF-40903 异常
     */
    @Test
    void rejectTask_notAssignee_throwsWf40903() {
        Task mockTask = buildMockTask("TASK_001", "PID_001", "OTHER");
        mockTaskQuery(mockTask);

        assertThatThrownBy(() -> taskOperationService.rejectTask(
                "TASK_001", "E001", "不符合"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40903");
    }

    /**
     * 转交时任务不存在，应抛出 WF-40403 异常
     */
    @Test
    void transferTask_taskNotFound_throwsWf40403() {
        mockTaskQuery(null);

        assertThatThrownBy(() -> taskOperationService.transferTask(
                "TASK_999", "E001", "E002", "出差"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 签收时 BizProcessMap 不存在，不应抛异常（仅跳过更新）
     */
    @Test
    void claimTask_noBizProcessMap_doesNotThrow() {
        Task mockTask = buildMockTask("TASK_001", "PID_001", null);
        mockTaskQuery(mockTask);
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(null);

        // 不应抛异常
        taskOperationService.claimTask("TASK_001", "E001");

        verify(taskService).claim("TASK_001", "E001");
        verify(bizProcessMapMapper, never()).updateById(any());
    }
}
