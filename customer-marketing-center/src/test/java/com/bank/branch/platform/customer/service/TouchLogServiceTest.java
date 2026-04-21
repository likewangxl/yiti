package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.TouchLog;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.enums.TouchTaskType;
import com.bank.branch.platform.customer.enums.SlaStatus;
import com.bank.branch.platform.customer.mapper.TouchLogMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TouchLogService 单元测试（TDD RED 阶段）
 * 使用 MockitoExtension，不需要 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class TouchLogServiceTest {

    @Mock
    private TouchLogMapper logMapper;

    @Mock
    private TouchTaskMapper taskMapper;

    @Mock
    private TouchTaskService touchTaskService;

    @InjectMocks
    private TouchLogService touchLogService;

    // ==================== addLog ====================

    @Test
    void addLog_shouldInsertAndReturn() {
        // given: 任务存在且 PENDING，没有重复日志
        // count 返回 2 表示非首次日志，不触发 PENDING→IN_PROGRESS 状态转移
        TouchTask task = buildPendingTask("task-001");
        when(taskMapper.selectById("task-001")).thenReturn(task);
        when(logMapper.selectByTaskIdAndClientUuid("task-001", "uuid-abc")).thenReturn(null);
        when(logMapper.insert(any(TouchLog.class))).thenReturn(1);
        when(logMapper.countByTaskId("task-001")).thenReturn(2L);

        // when
        TouchLog result = touchLogService.addLog(
                "task-001", "uuid-abc", "今日拜访客户，沟通良好",
                "[\"http://minio/photo1.jpg\"]", "E10001", "ORG_SZ_001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getTouchTaskId()).isEqualTo("task-001");
        assertThat(result.getClientUuid()).isEqualTo("uuid-abc");
        assertThat(result.getLogContent()).isEqualTo("今日拜访客户，沟通良好");
        assertThat(result.getPhotoUrls()).isEqualTo("[\"http://minio/photo1.jpg\"]");
        assertThat(result.getCreatedBy()).isEqualTo("E10001");
        assertThat(result.getOwnerOrgId()).isEqualTo("ORG_SZ_001");
        assertThat(result.getLogTime()).isNotNull();

        verify(logMapper).insert(any(TouchLog.class));
    }

    @Test
    void addLog_shouldReturnExistingWhenDuplicate() {
        // given: 相同的 touchTaskId + clientUuid 已存在（幂等场景）
        TouchTask task = buildPendingTask("task-001");
        when(taskMapper.selectById("task-001")).thenReturn(task);

        TouchLog existing = new TouchLog();
        existing.setId("log-existing-001");
        existing.setTouchTaskId("task-001");
        existing.setClientUuid("uuid-abc");
        existing.setLogContent("已存在的日志");
        when(logMapper.selectByTaskIdAndClientUuid("task-001", "uuid-abc")).thenReturn(existing);

        // when: 重复提交时直接返回已有记录，不再插入
        TouchLog result = touchLogService.addLog(
                "task-001", "uuid-abc", "今日拜访客户，沟通良好",
                null, "E10001", "ORG_SZ_001");

        // then: 返回已有记录
        assertThat(result.getId()).isEqualTo("log-existing-001");
        // 不应调用 insert
        verify(logMapper, never()).insert(any(TouchLog.class));
    }

    @Test
    void addLog_shouldThrowWhenDuplicateKeyException() {
        // given: 任务存在且 PENDING，幂等检查通过（返回 null），但 INSERT 发生唯一键冲突（并发场景）
        TouchTask task = buildPendingTask("task-001");
        when(taskMapper.selectById("task-001")).thenReturn(task);
        when(logMapper.selectByTaskIdAndClientUuid("task-001", "uuid-concurrent")).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_task_uuid")).when(logMapper).insert(any(TouchLog.class));

        // when/then: DuplicateKeyException 应被捕获并转为 TOUCH_LOG_DUPLICATE 业务异常
        assertThatThrownBy(() -> touchLogService.addLog(
                "task-001", "uuid-concurrent", "日志内容",
                null, "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_LOG_DUPLICATE.getCode());
    }

    @Test
    void addLog_firstLogTransitionsPendingToInProgress() {
        // given: 任务处于 PENDING 状态，count 返回 1 表示本次插入的是首条日志
        TouchTask task = buildPendingTask("T001");
        when(taskMapper.selectById("T001")).thenReturn(task);
        when(logMapper.selectByTaskIdAndClientUuid("T001", "UUID-1")).thenReturn(null);
        when(logMapper.insert(any(TouchLog.class))).thenReturn(1);
        when(logMapper.countByTaskId("T001")).thenReturn(1L);

        // when
        touchLogService.addLog("T001", "UUID-1", "首次访谈", null, "E001", "ORG001");

        // then: 首次日志触发 PENDING → IN_PROGRESS
        verify(touchTaskService).markInProgress("T001");
    }

    @Test
    void addLog_subsequentLogDoesNotTriggerTransition() {
        // given: 任务处于 IN_PROGRESS 状态（首次日志已提交），count 返回 2 表示非首次
        TouchTask task = buildInProgressTask("T001");
        when(taskMapper.selectById("T001")).thenReturn(task);
        when(logMapper.selectByTaskIdAndClientUuid("T001", "UUID-2")).thenReturn(null);
        when(logMapper.insert(any(TouchLog.class))).thenReturn(1);
        when(logMapper.countByTaskId("T001")).thenReturn(2L);

        // when
        touchLogService.addLog("T001", "UUID-2", "二次跟进", null, "E001", "ORG001");

        // then: 非首次日志不触发状态转移
        verify(touchTaskService, never()).markInProgress(anyString());
    }

    // ==================== listByTaskId ====================

    @Test
    void listByTaskId_shouldReturnLogs() {
        // given: 任务有多条日志
        TouchLog log1 = new TouchLog();
        log1.setId("log-001");
        log1.setTouchTaskId("task-001");
        log1.setLogContent("第一次拜访");
        TouchLog log2 = new TouchLog();
        log2.setId("log-002");
        log2.setTouchTaskId("task-001");
        log2.setLogContent("第二次拜访");

        when(logMapper.selectByTaskId("task-001")).thenReturn(Arrays.asList(log1, log2));

        // when
        List<TouchLog> result = touchLogService.listByTaskId("task-001");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("log-001");
        assertThat(result.get(1).getId()).isEqualTo("log-002");

        verify(logMapper).selectByTaskId("task-001");
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

    /**
     * 构建 IN_PROGRESS 状态的测试用触达任务
     */
    private TouchTask buildInProgressTask(String id) {
        TouchTask task = buildPendingTask(id);
        task.setTaskStatus(TouchTaskStatus.IN_PROGRESS.getCode());
        return task;
    }
}
