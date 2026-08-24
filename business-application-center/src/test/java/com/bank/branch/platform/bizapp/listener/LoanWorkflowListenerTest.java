package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.bizapp.event.LoanApprovedEvent;
import com.bank.branch.platform.bizapp.event.LoanRejectedEvent;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 * LoanWorkflowListener 单元测试（TDD）。
 * 覆盖 APPROVED / REJECTED / 幂等 / 注解结构 共 7 个用例。
 */
@ExtendWith(MockitoExtension.class)
class LoanWorkflowListenerTest {

    @Mock
    private LoanApplyMapper loanMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotifyApi notifyApi;

    @InjectMocks
    private LoanWorkflowListener loanWorkflowListener;

    // ==================== APPROVED 分支 ====================

    @Test
    @DisplayName("APPROVED 结果：conditionalUpdateStatus 更新为 COMPLETED，发布 LoanApprovedEvent")
    void onProcessCompleted_approved_updatesToCompleted_andPublishesApprovedEvent() {
        // given
        LoanApply loan = buildLoan("LOAN001");
        when(loanMapper.conditionalUpdateStatus("LOAN001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.COMPLETED.getCode(), "SYSTEM")).thenReturn(1);
        when(loanMapper.selectById("LOAN001")).thenReturn(loan);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent("PI001", "LOAN:LOAN001", "APPROVED", null);

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then: 使用条件更新，目标状态为 COMPLETED
        verify(loanMapper).conditionalUpdateStatus("LOAN001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.COMPLETED.getCode(), "SYSTEM");
        // then: 发布 LoanApprovedEvent
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(LoanApprovedEvent.class);
        LoanApprovedEvent approvedEvent = (LoanApprovedEvent) captor.getValue();
        assertThat(approvedEvent.getLoanId()).isEqualTo("LOAN001");

        ArgumentCaptor<NotificationCmd> notification = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi).sendNotification(notification.capture());
        assertThat(notification.getValue().getTitle()).isEqualTo("资产立项审批通过");
        assertThat(notification.getValue().getContent()).contains("您的资产立项申请已审批通过");
    }

    // ==================== REJECTED 分支 ====================

    @Test
    @DisplayName("REJECTED 结果：conditionalUpdateStatus 更新为 REJECTED，发布带 rejectReason 的 LoanRejectedEvent")
    void onProcessCompleted_rejected_updatesToRejected_andPublishesRejectedEvent() {
        // given
        LoanApply loan = buildLoan("la001");
        when(loanMapper.conditionalUpdateStatus("la001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.REJECTED.getCode(), "SYSTEM")).thenReturn(1);
        when(loanMapper.selectById("la001")).thenReturn(loan);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent("pi-1", "LOAN:la001", "REJECTED", "金额超限");

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then: 条件更新到 REJECTED
        verify(loanMapper).conditionalUpdateStatus("la001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.REJECTED.getCode(), "SYSTEM");
        // then: 发布 LoanRejectedEvent，携带 rejectReason
        ArgumentCaptor<LoanRejectedEvent> captor = ArgumentCaptor.forClass(LoanRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isEqualTo("金额超限");
        assertThat(captor.getValue().getLoanId()).isEqualTo("la001");
    }

    @Test
    @DisplayName("REJECTED 结果 reason=null：rejectReason 字段为 null 且不抛异常")
    void onProcessCompleted_rejected_reasonNull_doesNotThrow() {
        // given
        LoanApply loan = buildLoan("la002");
        when(loanMapper.conditionalUpdateStatus("la002", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.REJECTED.getCode(), "SYSTEM")).thenReturn(1);
        when(loanMapper.selectById("la002")).thenReturn(loan);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent("pi-2", "LOAN:la002", "REJECTED", null);

        // when / then
        assertThatCode(() -> loanWorkflowListener.onProcessCompleted(event)).doesNotThrowAnyException();
        ArgumentCaptor<LoanRejectedEvent> captor = ArgumentCaptor.forClass(LoanRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isNull();
    }

    // ==================== 幂等保护 ====================

    @Test
    @DisplayName("conditionalUpdateStatus 返回 0 时：不发布任何事件（幂等保护）")
    void onProcessCompleted_whenRowsAffectedZero_doesNotPublishEvent() {
        // given: 模拟已被其他实例处理（rowsAffected=0）
        when(loanMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent("pi-1", "LOAN:la001", "APPROVED", null);

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then: 不发布任何事件
        verify(eventPublisher, never()).publishEvent(any());
        // 不需要再 selectById
        verify(loanMapper, never()).selectById(anyString());
    }

    // ==================== 边界：非 LOAN 前缀 ====================

    @Test
    @DisplayName("非 LOAN: 前缀的 businessKey：直接忽略，不做任何操作")
    void onProcessCompleted_nonLoanBusinessKey_shouldIgnore() {
        // given
        ProcessCompletedEvent event =
                new ProcessCompletedEvent("PI002", "LEAD:LEAD001", "APPROVED", null);

        // when
        loanWorkflowListener.onProcessCompleted(event);

        // then
        verify(loanMapper, never()).conditionalUpdateStatus(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== 注解结构验证 ====================

    @Test
    @DisplayName("onProcessCompleted 方法应标注 @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)")
    void onProcessCompleted_shouldBeAnnotatedWithTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method method = LoanWorkflowListener.class.getMethod(
                "onProcessCompleted", ProcessCompletedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).as("方法应标注 @TransactionalEventListener").isNotNull();
        assertThat(annotation.phase())
                .as("phase 应为 AFTER_COMMIT")
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution())
                .as("fallbackExecution 应为 false：与 @Transactional(REQUIRES_NEW) 配合后无需 fallback 路径"
                        + "（与 customer.WorkflowCallbackListener P0 修复 pattern 一致）")
                .isFalse();

        // P0 bug 防御性修复：要求 @Transactional(REQUIRES_NEW)，让本方法内 publishEvent 在新事务内 publish，
        // 未来若新增下游 AFTER_COMMIT listener 订阅 LoanApproved/LoanRejected 也无需 fallbackExecution 即可正确触发
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).as("方法应标注 @Transactional 以让 publishEvent 在新事务内发布").isNotNull();
        assertThat(transactional.propagation())
                .as("propagation 应为 REQUIRES_NEW")
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor())
                .as("rollbackFor 应包含 Exception.class")
                .contains(Exception.class);
    }

    // ==================== 异常不传播 ====================

    @Test
    @DisplayName("处理过程中抛出异常：不应传播到调用方")
    void onProcessCompleted_exceptionInHandler_shouldNotPropagate() {
        // given: conditionalUpdateStatus 抛异常
        when(loanMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB Error"));

        // when / then
        assertThatCode(() -> loanWorkflowListener.onProcessCompleted(
                new ProcessCompletedEvent("PI003", "LOAN:LOAN001", "APPROVED", null)))
                .doesNotThrowAnyException();
    }

    // ==================== 辅助方法 ====================

    private LoanApply buildLoan(String id) {
        LoanApply loan = new LoanApply();
        loan.setId(id);
        loan.setApplyNo("LA20260414000001");
        loan.setCustId("CUST001");
        loan.setStatus(LoanStatus.IN_APPROVAL.getCode());
        loan.setOwnerOrgId("ORG001");
        loan.setCreatedBy("E001");
        loan.setDeleted(0);
        return loan;
    }
}
