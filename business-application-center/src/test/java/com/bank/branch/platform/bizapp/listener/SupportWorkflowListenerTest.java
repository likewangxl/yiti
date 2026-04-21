package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.event.SupportRejectedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SupportWorkflowListener 单元测试（TDD）。
 * 覆盖 APPROVED / REJECTED / 幂等 / 注解结构 共 8 个用例。
 */
@ExtendWith(MockitoExtension.class)
class SupportWorkflowListenerTest {

    @Mock
    private SupportRequestMapper supportMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SupportWorkflowListener listener;

    // ==================== APPROVED 分支 ====================

    @Test
    @DisplayName("APPROVED 结果：conditionalUpdateStatus 更新为 COMPLETED，发布 SupportCompletedEvent")
    void onProcessCompleted_approved_updatesToCompleted_andPublishesSupportCompletedEvent() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        sr.setAssignedEmpId("E20001");
        when(supportMapper.conditionalUpdateStatus("SR001", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR001")).thenReturn(sr);

        // when
        listener.onProcessCompleted(
                new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:SR001", "APPROVED", null));

        // then: 使用条件更新，目标状态为 COMPLETED
        verify(supportMapper).conditionalUpdateStatus("SR001", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM");
        // then: 发布 SupportCompletedEvent
        ArgumentCaptor<SupportCompletedEvent> captor = ArgumentCaptor.forClass(SupportCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getRequestId()).isEqualTo("SR001");
    }

    // ==================== REJECTED 分支 ====================

    @Test
    @DisplayName("REJECTED 结果：conditionalUpdateStatus 更新为 REJECTED，发布带 rejectReason 的 SupportRejectedEvent")
    void onProcessCompleted_rejected_updatesToRejected_andPublishesSupportRejectedEvent() {
        // given
        SupportRequest sr = buildRequest("SR002", SupportStatus.IN_APPROVAL);
        when(supportMapper.conditionalUpdateStatus("SR002", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.REJECTED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR002")).thenReturn(sr);

        ProcessCompletedListener.ProcessCompletedEvent event =
                new ProcessCompletedListener.ProcessCompletedEvent("pid-2", "SUPPORT:SR002", "REJECTED", "客户资质不足");

        // when
        listener.onProcessCompleted(event);

        // then: 条件更新到 REJECTED
        verify(supportMapper).conditionalUpdateStatus("SR002", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.REJECTED.getCode(), "SYSTEM");
        // then: 发布 SupportRejectedEvent，携带 rejectReason
        ArgumentCaptor<SupportRejectedEvent> captor = ArgumentCaptor.forClass(SupportRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isEqualTo("客户资质不足");
        assertThat(captor.getValue().getRequestId()).isEqualTo("SR002");
    }

    @Test
    @DisplayName("REJECTED 结果 reason=null：rejectReason 为 null 且不抛异常")
    void onProcessCompleted_rejected_reasonNull_doesNotThrow() {
        // given
        SupportRequest sr = buildRequest("SR003", SupportStatus.IN_APPROVAL);
        when(supportMapper.conditionalUpdateStatus("SR003", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.REJECTED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR003")).thenReturn(sr);

        ProcessCompletedListener.ProcessCompletedEvent event =
                new ProcessCompletedListener.ProcessCompletedEvent("pid-3", "SUPPORT:SR003", "REJECTED", null);

        // when / then
        assertThatCode(() -> listener.onProcessCompleted(event)).doesNotThrowAnyException();
        ArgumentCaptor<SupportRejectedEvent> captor = ArgumentCaptor.forClass(SupportRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isNull();
    }

    // ==================== 幂等保护 ====================

    @Test
    @DisplayName("conditionalUpdateStatus 返回 0 时：不发布任何事件（幂等保护）")
    void onProcessCompleted_whenRowsAffectedZero_doesNotPublishEvent() {
        // given
        when(supportMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        // when
        listener.onProcessCompleted(
                new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:SR001", "APPROVED", null));

        // then
        verify(eventPublisher, never()).publishEvent(any());
        verify(supportMapper, never()).selectById(anyString());
    }

    // ==================== 边界：非 SUPPORT 前缀 ====================

    @Test
    @DisplayName("非 SUPPORT: 前缀的 businessKey：直接忽略，不查询数据库")
    void onProcessCompleted_nonSupportBusinessKey_shouldIgnore() {
        // given: LOAN: 前缀，不是 SUPPORT:
        listener.onProcessCompleted(
                new ProcessCompletedListener.ProcessCompletedEvent("PID001", "LOAN:LA001", "APPROVED", null));

        // then
        verify(supportMapper, never()).conditionalUpdateStatus(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== 注解结构验证 ====================

    @Test
    @DisplayName("onProcessCompleted 方法应标注 @TransactionalEventListener(AFTER_COMMIT)")
    void onProcessCompleted_shouldBeAnnotatedWithTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method method = SupportWorkflowListener.class.getMethod(
                "onProcessCompleted", ProcessCompletedListener.ProcessCompletedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).as("方法应标注 @TransactionalEventListener").isNotNull();
        assertThat(annotation.phase())
                .as("phase 应为 AFTER_COMMIT")
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution())
                .as("fallbackExecution 应为 true（无事务时也执行）")
                .isTrue();
    }

    // ==================== 异常不传播 ====================

    @Test
    @DisplayName("处理过程中抛出异常：不应传播到调用方")
    void onProcessCompleted_exceptionInHandler_shouldNotPropagate() {
        // given
        when(supportMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB Error"));

        // when / then
        assertThatCode(() -> listener.onProcessCompleted(
                new ProcessCompletedListener.ProcessCompletedEvent("PID001", "SUPPORT:SR001", "APPROVED", null)))
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
        sr.setCreatedBy("E001");
        sr.setDeleted(0);
        return sr;
    }
}
