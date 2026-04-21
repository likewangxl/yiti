package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.SlaStatus;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.enums.TouchTaskType;
import com.bank.branch.platform.customer.event.TouchCompletedEvent;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TouchTaskService 单元测试（TDD RED 阶段）
 * 使用 MockitoExtension，不需要 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class TouchTaskServiceTest {

    @Mock
    private TouchTaskMapper taskMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private TouchTaskStateMachineService stateMachine = new TouchTaskStateMachineService();

    @InjectMocks
    private TouchTaskService touchTaskService;

    // ==================== createFromClaim ====================

    @Test
    void createFromClaim_shouldInsertPendingTask() {
        // given: 认领后创建首次触达任务
        when(taskMapper.insert(any(TouchTask.class))).thenReturn(1);

        // when
        TouchTask result = touchTaskService.createFromClaim("cust-001", "ORG_SZ_001", "E10001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getTaskNo()).startsWith("TOUCH_");
        assertThat(result.getCustId()).isEqualTo("cust-001");
        assertThat(result.getOrgId()).isEqualTo("ORG_SZ_001");
        assertThat(result.getAssigneeEmpId()).isEqualTo("E10001");
        assertThat(result.getTaskType()).isEqualTo(TouchTaskType.FIRST_TOUCH.getCode());
        assertThat(result.getTaskStatus()).isEqualTo(TouchTaskStatus.PENDING.getCode());
        assertThat(result.getSlaStatus()).isEqualTo(SlaStatus.GREEN.getCode());
        assertThat(result.getPlanFinishTime()).isNotNull();
        assertThat(result.getWarningTime()).isNotNull();
        // 计划完成时间应在预警时间之后
        assertThat(result.getPlanFinishTime()).isAfter(result.getWarningTime());
        // businessKey 格式: TOUCH:{taskId}
        assertThat(result.getBusinessKey()).isEqualTo("TOUCH:" + result.getId());

        verify(taskMapper).insert(any(TouchTask.class));
    }

    // ==================== complete ====================

    @Test
    void complete_shouldUpdateStatusToSuccess() {
        // given: PENDING 状态任务
        TouchTask task = buildPendingTask("task-001");
        when(taskMapper.selectById("task-001")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.complete("task-001");

        // then: 验证 updateById 中的任务状态更新为 SUCCESS
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        TouchTask updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo("task-001");
        assertThat(updated.getTaskStatus()).isEqualTo(TouchTaskStatus.SUCCESS.getCode());
        assertThat(updated.getSuccessTime()).isNotNull();

        // 验证发布了 TouchCompletedEvent
        ArgumentCaptor<TouchCompletedEvent> eventCaptor = ArgumentCaptor.forClass(TouchCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TouchCompletedEvent event = eventCaptor.getValue();
        assertThat(event.getTaskId()).isEqualTo("task-001");
        assertThat(event.getCustId()).isEqualTo("cust-001");
    }

    @Test
    void complete_shouldThrowWhenNotPending() {
        // given: 任务已完成，不允许再次完成
        TouchTask task = buildPendingTask("task-002");
        task.setTaskStatus(TouchTaskStatus.SUCCESS.getCode());
        when(taskMapper.selectById("task-002")).thenReturn(task);

        // when/then
        assertThatThrownBy(() -> touchTaskService.complete("task-002"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getCode());
    }

    // ==================== cancel ====================

    @Test
    void cancel_shouldUpdateStatusToCancelled() {
        // given: PENDING 状态任务
        TouchTask task = buildPendingTask("task-003");
        when(taskMapper.selectById("task-003")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when: reason 仅记日志，TouchTask 表无 cancelReason 字段
        touchTaskService.cancel("task-003", "客户拒绝拜访");

        // then: 验证 updateById 中的任务状态更新为 CANCELLED
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        TouchTask updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo("task-003");
        assertThat(updated.getTaskStatus()).isEqualTo(TouchTaskStatus.CANCELLED.getCode());
        assertThat(updated.getCancelTime()).isNotNull();
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnTask() {
        // given
        TouchTask task = buildPendingTask("task-004");
        when(taskMapper.selectById("task-004")).thenReturn(task);

        // when
        TouchTask result = touchTaskService.getById("task-004");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("task-004");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        // given: 任务不存在
        when(taskMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> touchTaskService.getById("not-exist"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode());
    }

    // ==================== listPage ====================

    @Test
    void listPage_shouldCalculateOffset() {
        // given: pageNo=3, pageSize=10 -> offset=(3-1)*10=20
        TouchTask t1 = buildPendingTask("task-021");
        TouchTask t2 = buildPendingTask("task-022");
        List<TouchTask> mockList = Arrays.asList(t1, t2);

        when(taskMapper.selectPage(isNull(), isNull(), isNull(), eq(20), eq(10))).thenReturn(mockList);
        when(taskMapper.countPage(isNull(), isNull(), isNull())).thenReturn(25L);

        // when
        PageResult<TouchTask> result = touchTaskService.listPage(null, null, null, 3, 10);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(3);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(25L);
        assertThat(result.getRecords()).hasSize(2);

        // 验证 offset 计算正确：(3-1)*10=20
        verify(taskMapper).selectPage(null, null, null, 20, 10);
        verify(taskMapper).countPage(null, null, null);
    }

    // ==================== refreshSla ====================

    @Test
    void refreshSla_shouldUpdateGreenToYellow() {
        // given: 任务已到预警时间但未超计划完成时间
        TouchTask task = buildPendingTask("task-sla-001");
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        // 预警时间在过去，计划完成时间在将来
        task.setWarningTime(LocalDateTime.now().minusMinutes(1));
        task.setPlanFinishTime(LocalDateTime.now().plusDays(2));

        when(taskMapper.selectPendingForSlaRefresh()).thenReturn(List.of(task));
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.refreshSla();

        // then: SLA 状态从 GREEN 更新为 YELLOW
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSlaStatus()).isEqualTo(SlaStatus.YELLOW.getCode());
    }

    @Test
    void refreshSla_shouldUpdateYellowToRed() {
        // given: 任务已超过计划完成时间
        TouchTask task = buildPendingTask("task-sla-002");
        task.setSlaStatus(SlaStatus.YELLOW.getCode());
        // 预警时间和计划完成时间都在过去
        task.setWarningTime(LocalDateTime.now().minusDays(3));
        task.setPlanFinishTime(LocalDateTime.now().minusMinutes(1));

        when(taskMapper.selectPendingForSlaRefresh()).thenReturn(List.of(task));
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.refreshSla();

        // then: SLA 状态更新为 RED（超期）
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSlaStatus()).isEqualTo(SlaStatus.RED.getCode());
    }

    // ==================== markInProgress ====================

    @Test
    void markInProgress_setsStatusFromPendingToInProgress() {
        // given
        TouchTask existing = new TouchTask();
        existing.setId("T001");
        existing.setTaskStatus("PENDING");
        when(taskMapper.selectById("T001")).thenReturn(existing);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.markInProgress("T001");

        // then
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTaskStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void markInProgress_throwsWhenCurrentStatusIsNotPending() {
        // given: 非 PENDING 状态（SUCCESS）不允许转移到 IN_PROGRESS
        TouchTask existing = new TouchTask();
        existing.setId("T001");
        existing.setTaskStatus("SUCCESS");
        when(taskMapper.selectById("T001")).thenReturn(existing);

        // when/then
        assertThatThrownBy(() -> touchTaskService.markInProgress("T001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    @Test
    void markInProgress_throwsWhenTaskNotFound() {
        // given: 任务不存在
        when(taskMapper.selectById("T_NA")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> touchTaskService.markInProgress("T_NA"))
                .isInstanceOf(BizException.class);
    }

    // ==================== 测试辅助方法 ====================

    /**
     * 构建 PENDING 状态的测试用触达任务
     */
    private TouchTask buildPendingTask(String id) {
        TouchTask task = new TouchTask();
        task.setId(id);
        task.setTaskNo("TOUCH_" + id);
        task.setCustId("cust-001");
        task.setOrgId("ORG_SZ_001");
        task.setAssigneeEmpId("E10001");
        task.setTaskType(TouchTaskType.FIRST_TOUCH.getCode());
        task.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        task.setPlanFinishTime(LocalDateTime.now().plusDays(7));
        task.setWarningTime(LocalDateTime.now().plusDays(5));
        task.setBusinessKey("TOUCH:" + id);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        return task;
    }
}
