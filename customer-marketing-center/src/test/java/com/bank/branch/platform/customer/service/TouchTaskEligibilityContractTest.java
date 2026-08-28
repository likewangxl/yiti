package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.TouchTaskType;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TouchTaskEligibilityContractTest {

    private TouchTaskMapper taskMapper;
    private TouchEligibilityService touchEligibilityService;
    private TouchTaskService touchTaskService;

    @BeforeEach
    void setUp() {
        taskMapper = mock(TouchTaskMapper.class);
        touchEligibilityService = mock(TouchEligibilityService.class);
        touchTaskService = new TouchTaskService(
                taskMapper,
                mock(ApplicationEventPublisher.class),
                new TouchTaskStateMachineService(),
                touchEligibilityService,
                mock(TouchWorklogMapper.class));
    }

    @Test
    void createFirstTouchTask_shouldValidateEligibilityBeforeInsert() {
        when(taskMapper.insert(any(TouchTask.class))).thenReturn(1);

        touchTaskService.createFirstTouchTask("1", "ORG_SZ_001", "E10001", null);

        verify(touchEligibilityService).assertEligible("1");
        verify(taskMapper).insert(any(TouchTask.class));
    }

    @Test
    void createFollowUpTask_shouldValidateEligibilityBeforeInsert() {
        when(taskMapper.insert(any(TouchTask.class))).thenReturn(1);

        TouchTask result = touchTaskService.createFollowUpTask(
                "2", "ORG_SZ_001", "E10001", "再次拜访", null);

        assertThat(result.getTaskType()).isEqualTo(TouchTaskType.FOLLOW_UP.getCode());
        verify(touchEligibilityService).assertEligible("2");
        verify(taskMapper).insert(any(TouchTask.class));
    }

    @Test
    void createFirstTouchTask_shouldNotInsertWhenEligibilityRejected() {
        doThrow(new BizException("CUST-42216", "该企业经判定已开户，无法再创建工作日志"))
                .when(touchEligibilityService).assertEligible("cust-opened");

        assertThatThrownBy(() -> touchTaskService.createFirstTouchTask(
                "cust-opened", "ORG_SZ_001", "E10001", null))
                .isInstanceOf(BizException.class)
                .hasMessage("该企业经判定已开户，无法再创建工作日志");
        verify(taskMapper, never()).insert(any(TouchTask.class));
    }
}
