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
        assertThat(result.getSlaStatus()).isEqualTo(SlaStatus.BLUE.getCode());
        assertThat(result.getPlanFinishTime()).isNotNull();
        assertThat(result.getWarningTime()).isNotNull();
        // 计划完成时间应在预警时间之后
        assertThat(result.getPlanFinishTime()).isAfter(result.getWarningTime());
        // businessKey 格式: TOUCH:{taskId}
        assertThat(result.getBusinessKey()).isEqualTo("TOUCH:" + result.getId());

        verify(taskMapper).insert(any(TouchTask.class));
    }

    // ==================== markSuccess ====================

    @Test
    void markSuccess_fromPending_shouldUpdateStatusToSuccess() {
        // given: PENDING 状态任务
        TouchTask task = buildPendingTask("task-001");
        when(taskMapper.selectById("task-001")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.markSuccess("task-001", "E10001", false);

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
    void markSuccess_fromInProgress_shouldUpdateStatusToSuccess() {
        // given: IN_PROGRESS 状态任务也允许完成
        TouchTask task = buildPendingTask("task-002");
        task.setTaskStatus(TouchTaskStatus.IN_PROGRESS.getCode());
        when(taskMapper.selectById("task-002")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.markSuccess("task-002", "E10001", false);

        // then: 验证状态更新为 SUCCESS
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTaskStatus()).isEqualTo(TouchTaskStatus.SUCCESS.getCode());
        assertThat(captor.getValue().getSuccessTime()).isNotNull();

        // 验证发布了 TouchCompletedEvent
        verify(eventPublisher).publishEvent(any(TouchCompletedEvent.class));
    }

    @Test
    void markSuccess_shouldThrowWhenAlreadySuccess() {
        // given: 任务已完成（终态），不允许再次完成
        TouchTask task = buildPendingTask("task-003");
        task.setTaskStatus(TouchTaskStatus.SUCCESS.getCode());
        when(taskMapper.selectById("task-003")).thenReturn(task);

        // when/then: 状态机校验失败，抛 CUST-40010
        assertThatThrownBy(() -> touchTaskService.markSuccess("task-003", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getCode());
    }

    @Test
    void markSuccess_shouldThrowWhenCancelled() {
        // given: 任务已取消（终态），不允许完成
        TouchTask task = buildPendingTask("task-004");
        task.setTaskStatus(TouchTaskStatus.CANCELLED.getCode());
        when(taskMapper.selectById("task-004")).thenReturn(task);

        // when/then: 状态机校验失败，抛 CUST-40010
        assertThatThrownBy(() -> touchTaskService.markSuccess("task-004", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getCode());
    }

    @Test
    void markSuccess_shouldThrowWhenTaskNotFound() {
        // given: 任务不存在
        when(taskMapper.selectById("not-exist")).thenReturn(null);

        // when/then: 抛 CUST-40405
        assertThatThrownBy(() -> touchTaskService.markSuccess("not-exist", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode());
    }

    // ==================== cancel ====================

    @Test
    void cancel_shouldUpdateStatusToCancelled() {
        // given: PENDING 状态任务
        TouchTask task = buildPendingTask("task-003");
        when(taskMapper.selectById("task-003")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when: reason 仅记日志，TouchTask 表无 cancelReason 字段
        touchTaskService.cancel("task-003", "客户拒绝拜访", "E10001", false);

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

    // ==================== cancel 状态机化 ====================

    @Test
    void cancel_allowsPending() {
        // given: PENDING 状态 → CANCELLED 合法
        TouchTask task = buildPendingTask("T-P");
        when(taskMapper.selectById("T-P")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when: 应当正常完成，不抛异常
        touchTaskService.cancel("T-P", "客户取消", "E10001", false);

        // then: 状态更新为 CANCELLED
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTaskStatus()).isEqualTo(TouchTaskStatus.CANCELLED.getCode());
        assertThat(captor.getValue().getCancelTime()).isNotNull();
    }

    @Test
    void cancel_allowsInProgress() {
        // given: IN_PROGRESS 状态 → CANCELLED 也合法（状态机化后新增支持）
        TouchTask task = buildPendingTask("T-IP");
        task.setTaskStatus(TouchTaskStatus.IN_PROGRESS.getCode());
        when(taskMapper.selectById("T-IP")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when: 应当正常完成，不抛异常
        touchTaskService.cancel("T-IP", "客户取消", "E10001", false);

        // then: 状态更新为 CANCELLED
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTaskStatus()).isEqualTo(TouchTaskStatus.CANCELLED.getCode());
    }

    @Test
    void markSuccess_shouldThrowCust40302WhenOperatorNotAssignee() {
        // P1C：操作人非任务执行人且非 admin → CUST-40302
        TouchTask task = buildPendingTask("task-other");
        task.setAssigneeEmpId("E_OTHER");
        when(taskMapper.selectById("task-other")).thenReturn(task);

        assertThatThrownBy(() -> touchTaskService.markSuccess("task-other", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode());
    }

    @Test
    void markSuccess_shouldAllowAdminBypass() {
        // P1C：admin 可绕过 assignee 校验
        TouchTask task = buildPendingTask("task-admin");
        task.setAssigneeEmpId("E_OTHER");
        when(taskMapper.selectById("task-admin")).thenReturn(task);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        touchTaskService.markSuccess("task-admin", "ADMIN001", true);

        verify(taskMapper).updateById(any(TouchTask.class));
    }

    @Test
    void cancel_shouldThrowCust40302WhenOperatorNotAssignee() {
        // P1C：操作人非任务执行人且非 admin → CUST-40302
        TouchTask task = buildPendingTask("T-perm");
        task.setAssigneeEmpId("E_OTHER");
        when(taskMapper.selectById("T-perm")).thenReturn(task);

        assertThatThrownBy(() -> touchTaskService.cancel("T-perm", "原因", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode());
    }

    @Test
    void cancel_rejectsAlreadySuccess() {
        // given: SUCCESS 终态不允许取消
        TouchTask t = new TouchTask();
        t.setId("T-S");
        t.setTaskStatus("SUCCESS");
        t.setAssigneeEmpId("E10001");  // P1C 40302 校验需要 assignee 匹配 operator
        when(taskMapper.selectById("T-S")).thenReturn(t);

        // when/then: 状态机校验失败，抛 CUST-40010
        assertThatThrownBy(() -> touchTaskService.cancel("T-S", "reason", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    @Test
    void cancel_rejectsAlreadyCancelled() {
        // given: CANCELLED 终态不允许再次取消
        TouchTask t = new TouchTask();
        t.setId("T-C");
        t.setTaskStatus("CANCELLED");
        t.setAssigneeEmpId("E10001");  // P1C 40302 校验需要 assignee 匹配 operator
        when(taskMapper.selectById("T-C")).thenReturn(t);

        // when/then: 状态机校验失败，抛 CUST-40010
        assertThatThrownBy(() -> touchTaskService.cancel("T-C", "reason", "E10001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    // ==================== refreshSla + slaWarning 同步 ====================

    @Test
    void refreshSla_setsSlaWarningWhenYellow() {
        // given: 任务进入预警窗口（warningTime 已过，planFinishTime 未到）
        TouchTask task = buildPendingTask("T-Y");
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        task.setWarningTime(LocalDateTime.now().minusMinutes(1));
        task.setPlanFinishTime(LocalDateTime.now().plusDays(2));

        when(taskMapper.selectPendingForSlaRefresh()).thenReturn(List.of(task));
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.refreshSla();

        // then: slaStatus=YELLOW 且 slaWarning=true
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSlaStatus()).isEqualTo(SlaStatus.YELLOW.getCode());
        assertThat(captor.getValue().getSlaWarning()).isTrue();
    }

    @Test
    void refreshSla_setsSlaWarningWhenRed() {
        // given: 超出计划完成时间（RED）
        TouchTask task = buildPendingTask("T-R");
        task.setSlaStatus(SlaStatus.YELLOW.getCode());
        task.setWarningTime(LocalDateTime.now().minusDays(3));
        task.setPlanFinishTime(LocalDateTime.now().minusMinutes(1));

        when(taskMapper.selectPendingForSlaRefresh()).thenReturn(List.of(task));
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        touchTaskService.refreshSla();

        // then: slaStatus=RED 且 slaWarning=true
        ArgumentCaptor<TouchTask> captor = ArgumentCaptor.forClass(TouchTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSlaStatus()).isEqualTo(SlaStatus.RED.getCode());
        assertThat(captor.getValue().getSlaWarning()).isTrue();
    }

    @Test
    void refreshSla_keepsSlaWarningFalseWhenGreen() {
        // given: 仍在 GREEN 状态（时间窗口内，无需变更 slaStatus）
        // 但为验证初始值，构造一个 slaWarning=false 场景：创建一个任务
        // 此任务 warningTime 还未到，calcNewSlaStatus 返回 null → 不调用 updateById
        // 所以改用一个已是 GREEN 但之前有 slaWarning=true 的场景，验证它被置为 false
        // 实际场景：任务从 YELLOW 恢复（不太可能，但用于单元测试边界验证）
        // 简化：构建一个已被设为 YELLOW 的任务，但时间已恢复 GREEN 区间（不可能）
        // → 换测试角度：GREEN 状态时 slaWarning 应为 false；
        //   用一个没有状态变化的场景（calcNewSlaStatus=null）来确认 updateById 不被调用
        TouchTask task = buildPendingTask("T-G");
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        task.setSlaWarning(false);
        // 预警时间未到（GREEN）
        task.setWarningTime(LocalDateTime.now().plusDays(3));
        task.setPlanFinishTime(LocalDateTime.now().plusDays(7));

        when(taskMapper.selectPendingForSlaRefresh()).thenReturn(List.of(task));

        // when
        touchTaskService.refreshSla();

        // then: 无状态变化，updateById 不被调用（GREEN 无需更新）
        verify(taskMapper, org.mockito.Mockito.never()).updateById(any(TouchTask.class));
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

    // ==================== listPageAdmin ====================

    @Test
    void listPageAdmin_shouldReturnAllOrgsData() {
        // given: 管理后台查询，不限机构，pageNo=1, pageSize=10
        TouchTask t1 = buildPendingTask("admin-task-001");
        t1.setOrgId("ORG001");
        TouchTask t2 = buildPendingTask("admin-task-002");
        t2.setOrgId("ORG002");
        List<TouchTask> mockList = List.of(t1, t2);

        when(taskMapper.selectAdminPage(isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(mockList);
        when(taskMapper.countAdminPage(isNull(), isNull(), isNull(), isNull())).thenReturn(2L);

        // when
        PageResult<TouchTask> result = touchTaskService.listPageAdmin(null, null, null, null, 1, 10);

        // then: 两个不同机构的任务都返回
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
    }

    // ==================== batchAssign ====================

    @Test
    void batchAssign_shouldUpdatePendingAndInProgressTasks() {
        // given: 两个任务（PENDING + IN_PROGRESS）都允许重分配
        TouchTask t1 = buildPendingTask("task-BA-1");
        t1.setTaskStatus("PENDING");
        TouchTask t2 = buildPendingTask("task-BA-2");
        t2.setTaskStatus("IN_PROGRESS");

        when(taskMapper.selectById("task-BA-1")).thenReturn(t1);
        when(taskMapper.selectById("task-BA-2")).thenReturn(t2);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        int updated = touchTaskService.batchAssign(List.of("task-BA-1", "task-BA-2"), "E99999");

        // then: 两条均成功更新
        assertThat(updated).isEqualTo(2);
    }

    @Test
    void batchAssign_shouldSkipCompletedTasks() {
        // given: 一个 PENDING，一个 SUCCESS（终态不允许重分配）
        TouchTask pending = buildPendingTask("task-BA-P");
        pending.setTaskStatus("PENDING");
        TouchTask success = buildPendingTask("task-BA-S");
        success.setTaskStatus("SUCCESS");

        when(taskMapper.selectById("task-BA-P")).thenReturn(pending);
        when(taskMapper.selectById("task-BA-S")).thenReturn(success);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        int updated = touchTaskService.batchAssign(List.of("task-BA-P", "task-BA-S"), "E99999");

        // then: 只有 PENDING 的任务被更新，SUCCESS 跳过
        assertThat(updated).isEqualTo(1);
    }

    @Test
    void batchAssign_shouldSkipNonExistentTasks() {
        // given: 一个存在（PENDING），一个不存在
        TouchTask existing = buildPendingTask("task-BA-E");
        existing.setTaskStatus("PENDING");

        when(taskMapper.selectById("task-BA-E")).thenReturn(existing);
        when(taskMapper.selectById("task-BA-NA")).thenReturn(null);
        when(taskMapper.updateById(any(TouchTask.class))).thenReturn(1);

        // when
        int updated = touchTaskService.batchAssign(List.of("task-BA-E", "task-BA-NA"), "E99999");

        // then: 只有存在的任务被更新，不存在的跳过
        assertThat(updated).isEqualTo(1);
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
