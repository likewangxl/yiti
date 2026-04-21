package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.ProcessStatus;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ProcessCompletedListener 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ProcessCompletedListenerTest {

    @Mock
    private BizProcessMapMapper bizProcessMapMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProcessCompletedListener processCompletedListener;

    @Test
    void notify_updatesMapToCompleted() {
        // Arrange
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceId()).thenReturn("PID_001");

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_001");
        map.setProcessInstanceId("PID_001");
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());

        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        // Act
        processCompletedListener.notify(execution);

        // Assert
        ArgumentCaptor<BizProcessMap> captor = ArgumentCaptor.forClass(BizProcessMap.class);
        verify(bizProcessMapMapper).updateById(captor.capture());

        BizProcessMap updated = captor.getValue();
        assertEquals(ProcessStatus.COMPLETED.getCode(), updated.getProcessStatus());
        assertNotNull(updated.getEndTime());
        assertNotNull(updated.getUpdatedTime());
    }

    @Test
    void notify_rejectedProcess_updatesMapToCancelled() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceId()).thenReturn("PID_003");
        when(execution.getVariable("approved")).thenReturn(Boolean.FALSE);

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_003");
        map.setProcessInstanceId("PID_003");
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());

        when(bizProcessMapMapper.selectByProcessInstanceId("PID_003")).thenReturn(map);

        processCompletedListener.notify(execution);

        ArgumentCaptor<BizProcessMap> captor = ArgumentCaptor.forClass(BizProcessMap.class);
        verify(bizProcessMapMapper).updateById(captor.capture());
        assertEquals(ProcessStatus.CANCELLED.getCode(), captor.getValue().getProcessStatus());
    }

    @Test
    void notify_noMapping_logsWarningAndSkips() {
        // Arrange
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceId()).thenReturn("PID_NOT_FOUND");

        when(bizProcessMapMapper.selectByProcessInstanceId("PID_NOT_FOUND")).thenReturn(null);

        // Act
        processCompletedListener.notify(execution);

        // Assert
        verify(bizProcessMapMapper, never()).updateById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void notify_publishesEvent() {
        // Arrange
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceId()).thenReturn("PID_002");

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_002");
        map.setProcessInstanceId("PID_002");
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());

        when(bizProcessMapMapper.selectByProcessInstanceId("PID_002")).thenReturn(map);

        // Act
        processCompletedListener.notify(execution);

        // Assert
        verify(eventPublisher).publishEvent(any(ProcessCompletedListener.ProcessCompletedEvent.class));
    }

    @Test
    @DisplayName("流程完成时应发布携带 outcome/reason 的 ProcessCompletedEvent")
    void publishesEventWithOutcomeAndReason() {
        // given: mock DelegateExecution 返回 approved=false, reason="金额超限"
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        when(execution.getVariable("approved")).thenReturn(Boolean.FALSE);
        when(execution.getVariable("reason")).thenReturn("金额超限");

        BizProcessMap map = new BizProcessMap();
        map.setBusinessKey("LOAN:la001");
        when(bizProcessMapMapper.selectByProcessInstanceId("pi-1")).thenReturn(map);

        // when
        processCompletedListener.notify(execution);

        // then: 捕获发布事件，断言 outcome=REJECTED + reason 正确
        ArgumentCaptor<ProcessCompletedListener.ProcessCompletedEvent> captor =
                ArgumentCaptor.forClass(ProcessCompletedListener.ProcessCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo("REJECTED");
        assertThat(captor.getValue().reason()).isEqualTo("金额超限");
        assertThat(captor.getValue().businessKey()).isEqualTo("LOAN:la001");
    }
}
