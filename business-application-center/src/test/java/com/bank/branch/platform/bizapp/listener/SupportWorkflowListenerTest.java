package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SupportWorkflowListener 单元测试（TDD）。
 * 共 5 个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class SupportWorkflowListenerTest {

    @Mock
    private SupportRequestMapper supportMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SupportWorkflowListener listener;

    @Test
    void onProcessCompleted_supportBusinessKey_shouldUpdateStatus() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        when(supportMapper.selectByBusinessKey("SUPPORT:SR001")).thenReturn(sr);
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        listener.onProcessCompleted(new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:SR001"));

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SupportStatus.COMPLETED.getCode());
    }

    @Test
    void onProcessCompleted_nonSupportBusinessKey_shouldIgnore() {
        // given: LOAN: 前缀，不是 SUPPORT:
        listener.onProcessCompleted(new ProcessCompletedListener.ProcessCompletedEvent("PID001", "LOAN:LA001"));

        // then: 不查询 support_request
        verify(supportMapper, never()).selectByBusinessKey(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onProcessCompleted_requestNotFound_shouldLogAndSkip() {
        // given: 数据库无对应记录
        when(supportMapper.selectByBusinessKey("SUPPORT:NOTEXIST")).thenReturn(null);

        // when / then: 不应抛异常，静默跳过
        assertThatCode(() -> listener.onProcessCompleted(
                new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:NOTEXIST")))
                .doesNotThrowAnyException();

        verify(supportMapper, never()).updateById(any());
    }

    @Test
    void onProcessCompleted_shouldPublishSupportCompletedEvent() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        sr.setAssignedEmpId("E20001");
        when(supportMapper.selectByBusinessKey("SUPPORT:SR001")).thenReturn(sr);
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        listener.onProcessCompleted(new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:SR001"));

        // then
        ArgumentCaptor<SupportCompletedEvent> captor = ArgumentCaptor.forClass(SupportCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getRequestId()).isEqualTo("SR001");
    }

    @Test
    void onProcessCompleted_exceptionInHandler_shouldNotPropagate() {
        // given: selectByBusinessKey 抛异常
        when(supportMapper.selectByBusinessKey("SUPPORT:SR001"))
                .thenThrow(new RuntimeException("DB Error"));

        // when / then: 异常不应传播到调用方
        assertThatCode(() -> listener.onProcessCompleted(
                new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:SR001")))
                .doesNotThrowAnyException();
    }

    // ==================== 辅助方法 ====================

    private SupportRequest buildRequest(String id, SupportStatus status) {
        SupportRequest sr = new SupportRequest();
        sr.setId(id);
        sr.setRequestNo("SR20260414000001");
        sr.setCustId("CUST001");
        sr.setStatus(status.getCode());
        sr.setBusinessKey("SUPPORT:" + id);
        sr.setOwnerOrgId("ORG001");
        sr.setDeleted(0);
        return sr;
    }
}
